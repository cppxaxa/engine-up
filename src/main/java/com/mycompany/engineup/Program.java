/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.engineup;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.PortForward;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.typesense.api.Client;

public class Program {

    private final static EngineUp gui = new EngineUp();

    public static void main(String[] args) throws Exception {
        gui.setVisible(true);
        out("Loading");

        // Is typesense accessible.
        StringBuilder builder = new StringBuilder();
        boolean typesenseConnected = checkTypesense(builder);
        out(builder.toString());
        
        PortForward portForward;
        
        if (!typesenseConnected) {
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
            
            while (!typesenseConnected) {
                out("Waiting 500 millis ...");
                Thread.sleep(500);
                
                try (PortForward pF = portForwardTypesense()) {
                    builder = new StringBuilder();
                    typesenseConnected = checkTypesense(builder);
                    out(builder.toString());
                }
                catch (EngineUpException | IOException
                        | KubernetesClientException ex) {

                    out("Port forward failure, " + ex.getClass() + ", "
                            + ex.getMessage());
                }
            }
            
            portForward = portForwardTypesense();
        }
        
        try {
            String q = "stark";
            org.typesense.model.SearchParameters searchParams =
                    makeQueryParam(q);
            
            out(serializeSearchParams(searchParams));
            
            org.typesense.model.SearchResult searchResults =
                    searchText(searchParams);
        
            out(serializeSearchResult(searchResults));
            
            // Backup typesense.
            builder = new StringBuilder();
            boolean success = backupTypesense(builder);
            out(builder.toString());
            
            if (success) {
                builder = new StringBuilder();
                packBackupFile(builder);
                out(builder.toString());
                
                builder = new StringBuilder();
                String backupfilePath = pullBackupFile(builder);
                out(builder.toString());
                out("Backup file path: " + backupfilePath);
                
                builder = new StringBuilder();
                deleteRemoteBackup(builder);
                out(builder.toString());
            }
        }
        catch (Exception ex) {
            out("Exception occurred, " + ex.getClass() + ", "
                    + ex.getMessage());
        }
        
        out("Completed");
    }

    private static void out(String message) {
        gui.getOutputTextArea().setText(
                gui.getOutputTextArea().getText() + message + '\n');
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
        builder.append("Found: ").append(searchResult.getFound()).append('\n');
        builder.append("Total Hits: ").append(searchResult.getHits().size())
                .append('\n');
        builder.append("Search Time (ms): ")
                .append(searchResult.getSearchTimeMs()).append('\n');
        
        // Print hits
        builder.append("Hits:" + '\n');
        
        for (org.typesense.model.SearchResultHit hit : searchResult.getHits()) {
            builder.append("Document: ").append(hit.getDocument()).append('\n');
            builder.append("Highlight:" + '\n');
            
            for (org.typesense.model.SearchHighlight highlight : hit
                    .getHighlights()) {
                
                builder.append("  Field: ").append(highlight.getField())
                        .append('\n');
                builder.append("    Matched Tokens: ")
                        .append(highlight.getMatchedTokens()).append('\n');
                builder.append("    Snippet: ").append(highlight.getSnippet())
                        .append('\n');
            }
            
            builder.append("Text Match: ").append(hit.getTextMatch()).append('\n');
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
        builder.append("Q: ").append(searchParams.getQ()).append('\n')
                .append("FilterBy: ").append(searchParams.getFilterBy())
                .append('\n')
                .append("SortBy: ").append(searchParams.getSortBy())
                .append('\n')
                .append("QueryBy: ").append(searchParams.getSortBy())
                .append('\n')
                .append("PerPage: ").append(searchParams.getPerPage())
                .append('\n')
                .append("Page: ").append(searchParams.getPage()).append('\n');
        
        return builder.toString();
    }

    private static String installTypesense() {
        try {
            StringBuilder builder = new StringBuilder();

            builder.append("We'll install the typesense in k8s cluster.")
                    .append('\n');

            ClassLoader classLoader = Thread.currentThread()
                    .getContextClassLoader();

            java.net.URL typesenseUrl = classLoader.getResource(
                    "typesense.yml");

            java.nio.file.Path typesensePath = java.nio.file.Paths.get(
                    typesenseUrl.toURI());
            
            List<String> lines = java.nio.file.Files.readAllLines(
                    typesensePath.toAbsolutePath());
            
            String ymlContent = String.join("\n", lines);

            builder.append("Install typesense yml:").append('\n');
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

    private static boolean backupTypesense(StringBuilder builder) {
        // Initialize the Typesense client
        org.typesense.api.Client client = getTypesenseClient();
        
        // Perform the snapshot
        Map<String, String> query = new HashMap<>();
        query.put("snapshot_path", "/data/snapshot");
        
        try
        {
            Map<String, String> result = client.operations
                    .perform("snapshot", query);
            
            if (result.containsKey("success")) {
                Object success = result.get("success");
                
                if ((boolean) success) {
                    builder.append("backup success").append('\n');
                    return true;
                }
            }
            
            builder.append("backup result, ").append(result.toString());
        }
        catch (Exception ex) {
            builder.append("backup exception: ").append(ex.getMessage())
                    .append('\n');
            return false;
        }
        
        builder.append("backup unexpected code path").append('\n');
        return false;
    }

    private static boolean checkTypesense(StringBuilder builder) {
        Client client = getTypesenseClient();
        
        try {
            Map<String, Object> result = client.health.retrieve();
            
            if (result.containsKey("ok")) {
                builder.append("typesense health ok").append('\n');
                return (boolean) result.get("ok");
            }
        }
        catch (Exception ex) {
            builder.append("typesense health exception, ")
                    .append(ex.getMessage()).append('\n');
            return false;
        }
        
        return false;
    }

    private static Client getTypesenseClient() {
        // Initialize the Typesense client
        List<org.typesense.resources.Node> nodes = new ArrayList<>();
        nodes.add(new org.typesense.resources
                .Node("http", "localhost", "8108"));
        org.typesense.api.Configuration configuration =
                new org.typesense.api.Configuration(
                        nodes, Duration.ofSeconds(2), "Welcome@1234");
        org.typesense.api.Client client = new org.typesense.api.Client(
                configuration);
        return client;
    }

    private static String pullBackupFile(StringBuilder builder) {
        String backupFilePath = "/data/tsbak.tar";
        String filename = "tsbak.tar";
        String downloadFilePath = java.nio.file.Paths.get(
                System.getProperty("java.io.tmpdir"), filename)
                .toAbsolutePath().toString();
        
        java.nio.file.Path downloadPath = new java.io.File(downloadFilePath)
            .toPath();
        
        builder.append("Connecting to typesense pod.")
                .append('\n');
        
        String typesenseDataPodName = getTypesenseDataPodName();
        
        KubernetesClient kclient = new KubernetesClientBuilder().build();
        
        kclient.pods().inNamespace("typesense").withName(typesenseDataPodName)
                .file(backupFilePath)
                .copy(downloadPath);
        
        builder.append("Downloaded: ").append(downloadFilePath).append('\n');
        
        return downloadFilePath;
    }

    private static boolean packBackupFile(StringBuilder builder) {
        builder.append("Packing remote backup").append('\n');
        
        java.io.ByteArrayOutputStream outStream =
                new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream errStream =
                new java.io.ByteArrayOutputStream();
        
        String[] command = new String[] {
            "tar", "-cf", "./data/tsbak.tar", "./data/snapshot"
        };
        
        executeShellCommand(outStream, errStream, builder, command);
        
        builder.append("Packed remote backup");
        return true;
    }

    private static boolean deleteRemoteBackup(final StringBuilder builder) {
        builder.append("Deleting remote backup").append('\n');
        
        java.io.ByteArrayOutputStream outStream =
                new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream errStream =
                new java.io.ByteArrayOutputStream();
        
        String[] command = new String[] {
            "rm", "-f", "/data/tsbak.tar"
        };
        
        executeShellCommand(outStream, errStream, builder, command);
        
        command = new String[] {
            "rm", "-rf", "/data/snapshot"
        };
        
        executeShellCommand(outStream, errStream, builder, command);
        
        builder.append("Deleted remote backup");
        return true;
    }

    private static void executeShellCommand(
            ByteArrayOutputStream outStream,
            ByteArrayOutputStream errStream,
            final StringBuilder builder, String[] command) {
        
        KubernetesClient kclient = new KubernetesClientBuilder().build();
        
        ExecListener execListener = new ExecListener() {

            @Override
            public void onOpen() {
                builder.append("exec open\n");
            }

            @Override
            public void onFailure(Throwable t, ExecListener.Response failureResponse) {
                builder.append("on failure").append("\n")
                        .append(t.getClass()).append("\n")
                        .append(failureResponse.toString())
                        .append("\n");
            }

            @Override
            public void onClose(int i, String string) {
                builder.append("exec close\n")
                        .append(Integer.toString(i)).append("\n")
                        .append(string).append("\n");
            }
        };
        
        String typesenseDataPodName = getTypesenseDataPodName();
        
        kclient.pods().inNamespace("typesense")
                .withName(typesenseDataPodName)
                .writingOutput(outStream)
                .writingError(errStream)
                .usingListener(execListener)
                .exec(command);
    }

    private static String getTypesenseDataPodName() {
//        KubernetesClient client = new KubernetesClientBuilder().build();
//        
//        List<Pod> pods = client.pods().inNamespace("typesense").list()
//                .getItems();
//        
//        for (Pod pod: pods) {
//            if (pod.getMetadata().getName().startsWith("typesense")) {
//                return pod.getMetadata().getName();
//            }
//        }
//        
//        return null;
        
        return "typesense-agent-ssh";
    }
}
