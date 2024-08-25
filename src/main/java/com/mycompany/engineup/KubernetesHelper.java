/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.engineup;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class KubernetesHelper {
    private final KubernetesClient client;

    public KubernetesHelper(KubernetesClient client) {
        this.client = client;
    }
    
    public Collection<HasMetadata> apply(InputStream yamlStream) {
        Object unmarshalled = Serialization.unmarshal(yamlStream);
        Collection<HasMetadata> entities;
        if (unmarshalled instanceof Collection) {
            entities = (Collection) unmarshalled;
        } else if (unmarshalled instanceof KubernetesResourceList) {
            entities = ((KubernetesResourceList) unmarshalled).getItems();
        } else {
            entities = new LinkedList<>();
            HasMetadata m = (HasMetadata) unmarshalled;
            entities.add(m);
        }
        
        List<HasMetadata> result = new LinkedList<>();
        
        for (HasMetadata input: entities) {
            result.add(createOrPatch(input));
        }
        
        return result;
    }
    
    protected <T extends HasMetadata> T createOrPatch(T inEntity) {
        String annotationName = "kubectl.kubernetes.io/last-applied-configuration";
        if (inEntity.getMetadata().getAnnotations() == null) {
            inEntity.getMetadata().setAnnotations(new HashMap<String, String>());
        }
        inEntity.getMetadata().getAnnotations().put(annotationName, Serialization.asJson(inEntity));
        NamespaceableResource<T> resource = client.resource(inEntity);
        T serverEntity = resource.get();
        T outEntity;
        if (serverEntity == null) {
            outEntity = resource.create();
        } else {
            String lastAppliedConfiguration = serverEntity.getMetadata().getAnnotations().get(annotationName);
            if (lastAppliedConfiguration == null) {
                String msg = String.format("Could not find annotation '%s' for entity '%s'", annotationName, inEntity.getMetadata().getName());
                throw new RuntimeException(msg);
            }
            T lastAppliedEntity = Serialization.unmarshal(lastAppliedConfiguration);
            outEntity = client.resource(lastAppliedEntity).patch(inEntity);
        }
        return outEntity;
    }
}