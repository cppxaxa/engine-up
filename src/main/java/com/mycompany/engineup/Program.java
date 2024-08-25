package com.mycompany.engineup;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.PortForward;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

public class Program {

    private final static EngineUp gui = new EngineUp();

    public static void main(String[] args) {
        gui.setVisible(true);
        out("Loading");

        boolean connectionSuccess = checkKubectlConnection();
        
        if (!connectionSuccess) {
            out(manKubectlConnection());
            return;
        }
        
        boolean portForwardTestSuccess = checkPortForwardPossible();
        
        if (!portForwardTestSuccess) {
            out("Port forward test failure.");
            out(installTypesense());
        }
        
        boolean typesenseNamespaceFound = checkTypesenseNamespacePresent();
        
        if (!typesenseNamespaceFound) {
            out("typesense namespace not found.");
            out(installTypesense());
        }
        
        try (PortForward portForward = portForwardTypesense()) {
            String q = "stark";
            org.typesense.model.SearchParameters searchParams =
                    makeQueryParam(q);
            
            out(serializeSearchParams(searchParams));
            
            org.typesense.model.SearchResult searchResults =
                    searchText(searchParams);
        
            out(serializeSearchResult(searchResults));
        }
        catch (IOException | KubernetesClientException ex) {
            out("Port forward failure, " + ex.getMessage());
        }
        catch (Exception ex) {
            out("Search failed, " + ex.getClass() + ", " + ex.getMessage());
        }
        
        out("Completed");
    }

    private static org.typesense.model.SearchParameters makeQueryParam(
            String q) {

        org.typesense.model.SearchParameters searchParams =
                new org.typesense.model.SearchParameters();
        searchParams.setQ(q);
        searchParams.setQueryBy("company_name");
        //        searchParams.setFilterBy("num_employees:>100");
        //        searchParams.setSortBy("num_employees:desc");
        searchParams.setPerPage(50);
        searchParams.setPage(1);
        return searchParams;
    }
    
    private static String serializeSearchResult(
        org.typesense.model.SearchResult searchResult) {
        
        StringBuilder builder = new StringBuilder();
        
        builder.append("Search Result:" + '\n');
        builder.append("Found: " + searchResult.getFound() + '\n');
        builder.append("Total Hits: " + searchResult.getHits().size() + '\n');
        builder.append("Search Time (ms): " + searchResult.getSearchTimeMs()
                + '\n');
        
        // Print hits
        builder.append("Hits:" + '\n');
        
        for (org.typesense.model.SearchResultHit hit : searchResult.getHits()) {
            builder.append("Document: " + hit.getDocument() + '\n');
            builder.append("Highlight:" + '\n');
            
            for (org.typesense.model.SearchHighlight highlight : hit
                    .getHighlights()) {
                
                builder.append("  Field: " + highlight.getField() + '\n');
                builder.append("    Matched Tokens: "
                        + highlight.getMatchedTokens() + '\n');
                builder.append("    Snippet: " + highlight.getSnippet() + '\n');
            }
            
            builder.append("Text Match: " + hit.getTextMatch() + '\n');
        }
        
        return builder.toString();
    }

    private static PortForward portForwardTypesense()
            throws IOException, KubernetesClientException, EngineUpException {
        
        KubernetesClient kclient = new KubernetesClientBuilder().build();
        
        List<Service> services = kclient.services().inNamespace("typesense")
                .list().getItems();
        
        if (services.isEmpty()) {
            throw new EngineUpException("typesense namespace not found");
        }
        
        Service service = services.get(0);

        return kclient.services()
                .inNamespace(service.getMetadata().getNamespace())
                .withName(service.getMetadata().getName())
                .portForward(8108, 8108);
    }

    private static String manKubectlConnection() {
        StringBuilder builder = new StringBuilder();
        builder.append("Please fix the kubectl connection with the "
                + "kubernetes cluster." + '\n');
        builder.append("One way is to use the VirtualBox, "
                + "which involves 2 steps." + '\n');
        builder.append("1. Install k3os in VM." + '\n');
        builder.append("2. Expose the VM 6443 to host 6443." + '\n');
        builder.append("Boot up the k3os iso." + '\n');
        builder.append("Install the k3os in a virtual harddisk." + '\n');
        builder.append("Specify the password during the k3os installation."
                + '\n');
        builder.append("Launch the VM." + '\n');
        builder.append("Login to rancher user." + '\n');
        builder.append("Bring out a copy of the file into host: "
                + "/etc/rancher/k3s/k3s.yaml" + '\n');
        builder.append("You can use service like termbin.com" + '\n');
        builder.append("Use command `cat /etc/rancher/k3s/k3s.yaml | "
                + "nc termbin.com 9999" + '\n');
        builder.append("In your host machine, open the termbin.com "
                + "link from above." + '\n');
        builder.append("Put the file in host machine, say k3s.yml" + '\n');
        builder.append("Create an environment variable KUBECONFIG." + '\n');
        builder.append("Set the value to k3s.yml absolute path." + '\n');
        builder.append("In virtualbox, expose the 6443 port to host 6443."
                + '\n');
        builder.append("Check if the kubectl command works e.g. "
                + "kubectl cluster-info" + '\n');
        
        return builder.toString();
    }

    private static boolean checkKubectlConnection() {
        KubernetesClient kclient = new KubernetesClientBuilder().build();
        
        try {
            kclient.getKubernetesVersion();
            return true;
        }
        catch (KubernetesClientException ex) {
            return false;
        }
    }

    private static org.typesense.model.SearchResult searchText(
            org.typesense.model.SearchParameters searchParams)
            throws Exception {

        // Initialize the Typesense client
        List<org.typesense.resources.Node> nodes = new ArrayList<>();
        nodes.add(new org.typesense.resources
                .Node("http", "localhost", "8108"));
        
        org.typesense.api.Configuration configuration =
                new org.typesense.api.Configuration(
                        nodes, Duration.ofSeconds(2), "Welcome@1234");
        org.typesense.api.Client client = new org.typesense.api.Client(
                configuration);
        
        // Perform the search
        org.typesense.model.SearchResult searchResult = client
                .collections("companies").documents().search(searchParams);
        
        return searchResult;
    }

    private static String serializeSearchParams(
            org.typesense.model.SearchParameters searchParams) {
        
        StringBuilder builder = new StringBuilder();
        
        builder.append("SearchParams\n");
        builder.append("Q: " + searchParams.getQ() + '\n');
        builder.append("FilterBy: " + searchParams.getFilterBy() + '\n');
        builder.append("SortBy: " + searchParams.getSortBy() + '\n');
        builder.append("QueryBy: " + searchParams.getSortBy() + '\n');
        builder.append("PerPage: " + searchParams.getPerPage() + '\n');
        builder.append("Page: " + searchParams.getPage() + '\n');
        
        return builder.toString();
    }

    private static String installTypesense() {
        try {
            StringBuilder builder = new StringBuilder();

            builder.append("We'll install the typesense in k8s cluster."
                    + '\n');

            ClassLoader classLoader = Thread.currentThread()
                    .getContextClassLoader();

            java.net.URL typesenseUrl = classLoader.getResource(
                    "typesense.yml");

            java.nio.file.Path typesensePath = java.nio.file.Paths.get(
                    typesenseUrl.toURI());
            
            List<String> lines = java.nio.file.Files.readAllLines(
                    typesensePath.toAbsolutePath());
            
            String ymlContent = String.join("\n", lines);

            builder.append("Install typesense yml:" + '\n');
            builder.append(ymlContent);
            builder.append('\n');
            
            // Apply the yml.
            KubernetesClient kclient = new KubernetesClientBuilder().build();
            KubernetesHelper helper = new KubernetesHelper(kclient);
            java.io.InputStream yamlStream = classLoader
                    .getResourceAsStream("typesense.yml");
            
            helper.apply(yamlStream);
            
            return builder.toString();
        }
        catch (IOException | java.net.URISyntaxException ex) {
            return "Install typesense, " + ex.getClass() + ", "
                    + ex.getMessage();
        }
    }

    private static boolean checkPortForwardPossible() {
        try (PortForward portForward = portForwardTypesense())
        { }
        catch (EngineUpException | IOException ex) {
            if (ex.getMessage().startsWith("port out of range:")) {
                return false;
            }
        }
        
        return true;
    }

    private static boolean checkTypesenseNamespacePresent() {
        try (PortForward portForward = portForwardTypesense())
        { }
        catch (EngineUpException | IOException ex) {
            if (ex.getMessage().startsWith("typesense namespace not found")) {
                return false;
            }
        }
        
        return true;
    }
    
//    private static void listPods() {
//        try {
//            // List pods.
//            ApiClient client = Config.defaultClient();
//            Configuration.setDefaultApiClient(client);
//
//            CoreV1Api api = new CoreV1Api();
//            V1PodList pods = api.listPodForAllNamespaces().execute();
//
//            gui.getOutputTextArea().setText("");
//
//            for (V1Pod podEl : pods.getItems()) {
//                out(gui.getOutputTextArea().getText()
//                        + podEl.getMetadata().getName());
//            }
//        } catch (IOException | ApiException ex) {
//            out("Failed" + '\n' + ex.getMessage());
//        }
//    }

    private static void out(String message) {
        gui.getOutputTextArea().setText(
                gui.getOutputTextArea().getText() + message + '\n');
    }
}
