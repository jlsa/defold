package com.dynamo.cr.contenteditor.scene;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.dynamo.cr.contenteditor.math.MathUtil;
import com.dynamo.cr.contenteditor.resource.IResourceLoaderFactory;
import com.dynamo.ddf.DDF;
import com.dynamo.gameobject.ddf.GameObject;
import com.dynamo.gameobject.ddf.GameObject.CollectionDesc;
import com.dynamo.gameobject.ddf.GameObject.CollectionInstanceDesc;
import com.dynamo.gameobject.ddf.GameObject.InstanceDesc;

public class CollectionNodeLoader implements INodeLoader {

    @Override
    public Node load(IProgressMonitor monitor, Scene scene, String name, InputStream stream, INodeLoaderFactory factory, IResourceLoaderFactory resourceFactory, Node parent) throws IOException,
            LoaderException, CoreException {

        InputStreamReader reader = new InputStreamReader(stream);

        GameObject.CollectionDesc desc = DDF.loadTextFormat(reader, GameObject.CollectionDesc.class);
        monitor.beginTask(name, desc.m_Instances.size() + desc.m_CollectionInstances.size());

        Map<String, Node> idToNode = new HashMap<String, Node>();
        CollectionNode node = new CollectionNode(scene, desc.m_Name, name);
        for (InstanceDesc id : desc.m_Instances) {
            Node prototype;
            try {
                prototype = factory.load(monitor, scene, id.m_Prototype, null);
            }
            catch (IOException e) {
                prototype = new BrokenNode(scene, id.m_Prototype, e.getMessage());
                factory.reportError(e.getMessage());
            }
            monitor.worked(1);

            InstanceNode in = new InstanceNode(scene, id.m_Id, id.m_Prototype, prototype);
            idToNode.put(id.m_Id, in);
            in.setLocalTranslation(MathUtil.toVector4(id.m_Position));
            in.setLocalRotation(MathUtil.toQuat4(id.m_Rotation));
            node.addNode(in);
        }

        for (InstanceDesc id : desc.m_Instances) {
            Node parentInstance = idToNode.get(id.m_Id);
            for (String child_id : id.m_Children) {
                Node child = idToNode.get(child_id);
                if (child == null)
                    throw new LoaderException(String.format("Child %s not found", child_id));

                node.removeNode(child);
                parentInstance.addNode(child);
            }
        }

        for (CollectionInstanceDesc cid : desc.m_CollectionInstances) {
            // detect recursion
            String ancestorCollection = name;
            Node subNode;
            if (!name.equals(cid.m_Collection) && parent != null) {
                Node ancestor = parent;
                ancestorCollection = ((CollectionNode)parent).getResource();
                while (!ancestorCollection.equals(cid.m_Collection) && ancestor != null) {
                    ancestor = ancestor.getParent();
                    if (ancestor != null && ancestor instanceof CollectionNode) {
                        ancestorCollection = ((CollectionNode)ancestor).getResource();
                    }
                }
            }
            if (ancestorCollection.equals(cid.m_Collection)) {
                subNode = new BrokenNode(scene, cid.m_Id, "A collection can not have collection instances which point to the same resource.");
            } else {
                Node sub_collection = factory.load(monitor, scene, cid.m_Collection, node);
                monitor.worked(1);

                subNode = new CollectionInstanceNode(scene, cid.m_Id, cid.m_Collection, sub_collection);

                subNode.setLocalTranslation(MathUtil.toVector4(cid.m_Position));
                subNode.setLocalRotation(MathUtil.toQuat4(cid.m_Rotation));
            }
            node.addNode(subNode);
        }

        return node;
    }

    @Override
    public void save(IProgressMonitor monitor, String name, Node node, OutputStream stream,
            INodeLoaderFactory loaderFactory) throws IOException, LoaderException {
        CollectionNode coll_node = (CollectionNode) node;
        CollectionDesc desc = coll_node.getDescriptor();

        OutputStreamWriter writer = new OutputStreamWriter(stream);
        DDF.saveTextFormat(desc, writer);
        writer.close();
    }
}

