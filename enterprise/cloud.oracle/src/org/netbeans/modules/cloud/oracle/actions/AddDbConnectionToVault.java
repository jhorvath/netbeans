/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.cloud.oracle.actions;

import com.oracle.bmc.devops.DevopsClient;
import com.oracle.bmc.devops.model.DeployArtifactSource;
import com.oracle.bmc.devops.model.DeployArtifactSummary;
import com.oracle.bmc.devops.model.InlineDeployArtifactSource;
import com.oracle.bmc.devops.model.UpdateDeployArtifactDetails;
import com.oracle.bmc.devops.requests.GetDeployArtifactRequest;
import com.oracle.bmc.devops.requests.ListDeployArtifactsRequest;
import com.oracle.bmc.devops.requests.UpdateDeployArtifactRequest;
import com.oracle.bmc.devops.responses.GetDeployArtifactResponse;
import com.oracle.bmc.devops.responses.ListDeployArtifactsResponse;
import org.netbeans.api.db.explorer.DatabaseConnection;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.vault.VaultsClient;
import com.oracle.bmc.vault.model.Base64SecretContentDetails;
import com.oracle.bmc.vault.model.CreateSecretDetails;
import com.oracle.bmc.vault.model.SecretContentDetails;
import com.oracle.bmc.vault.model.SecretReuseRule;
import com.oracle.bmc.vault.model.UpdateSecretDetails;
import com.oracle.bmc.vault.requests.CreateSecretRequest;
import com.oracle.bmc.vault.requests.ListSecretsRequest;
import com.oracle.bmc.vault.requests.UpdateSecretRequest;
import com.oracle.bmc.vault.responses.ListSecretsResponse;
import com.oracle.bmc.vault.responses.UpdateSecretResponse;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.netbeans.modules.cloud.oracle.OCIManager;
import static org.netbeans.modules.cloud.oracle.OCIManager.getDefault;
import static org.netbeans.modules.cloud.oracle.actions.Bundle.DatasourceName;
import org.netbeans.modules.cloud.oracle.assets.AbstractStep;
import org.netbeans.modules.cloud.oracle.assets.DependencyUtils;
import org.netbeans.modules.cloud.oracle.assets.Steps;
import org.netbeans.modules.cloud.oracle.assets.Steps.CompartmentStep;
import org.netbeans.modules.cloud.oracle.assets.Steps.DevopsStep;
import org.netbeans.modules.cloud.oracle.assets.Steps.NextStepProvider;
import org.netbeans.modules.cloud.oracle.assets.Steps.ProjectStep;
import org.netbeans.modules.cloud.oracle.assets.Steps.TenancyStep;
import org.netbeans.modules.cloud.oracle.assets.Steps.Values;
import org.netbeans.modules.cloud.oracle.assets.Steps.VaultStep;
import org.netbeans.modules.cloud.oracle.devops.DevopsProjectItem;
import org.netbeans.modules.cloud.oracle.items.OCIItem;
import org.netbeans.modules.cloud.oracle.vault.KeyItem;
import org.netbeans.modules.cloud.oracle.vault.KeyNode;
import org.netbeans.modules.cloud.oracle.vault.SecretItem;
import org.netbeans.modules.cloud.oracle.vault.SecretNode;
import org.netbeans.modules.cloud.oracle.vault.VaultItem;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.NotifyDescriptor.QuickPick.Item;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Jan Horvath
 */
@ActionID(
        category = "Tools",
        id = "org.netbeans.modules.cloud.oracle.actions.AddDbConnectionToVault"
)
@ActionRegistration(
        displayName = "#AddADBToVault",
        asynchronous = true
)
@ActionReferences(value = {
    @ActionReference(path = "Cloud/Oracle/Databases/Actions", position = 250)
})
@NbBundle.Messages({
    "AddADBToVault=Add Oracle Autonomous DB details to OCI Vault",
    "SelectKey=Select Key",
    "SelectVault=Select Vault",
    "SecretsCreated=Secrets were created or updated",
    "NoKeys=No keys in this Vault. Select another one.",
    "DatasourceName=Datasource Name",
    "AddVersion=Add new versions",
    "Cancel=Cancel",
    "SecretExists=Secrets with name {0} already exists",
    "NoProfile=There is not any OCI profile in the config",
    "NoCompartment=There are no compartments in the Tenancy",
    "Password=Enter password for Database user {0}",
    "NoConfigMap=No ConfigMap found in the Devops project {0}",
    "ConfigmapUpdateFailed=Failed to update ConfigMap",
    "CreatingSecret=Creating secret {0}",
    "UpdatingSecret=Updating secret {0}",
    "UpdatingVault=Updating {0} Vault",
    "ReadingSecrets=Reading existing Secrets",
    "DatasourceEmpty=Datasource name cannot be empty"
})
public class AddDbConnectionToVault implements ActionListener {

    private static final Logger LOG = Logger.getLogger(AddDbConnectionToVault.class.getName());

    private final DatabaseConnection context;

    public AddDbConnectionToVault(DatabaseConnection context) {
        this.context = context;
    }

    class KeyStep extends AbstractStep<KeyItem> {
        private Map<String, KeyItem> keys = null;
        private KeyItem selected;
        private VaultItem vault;
        private Lookup lookup;

        @Override
        public void prepare(ProgressHandle h) {
            keys = getKeys(vault);
        }

        @Override
        public boolean onlyOneChoice() {
            return keys.size() == 1;
        }

        @Override
        public NotifyDescriptor createInput() {
            if (keys.size() > 1) {
                return createQuickPick(keys, Bundle.SelectKey());
            }
            if (keys.isEmpty()) {
                return new NotifyDescriptor.QuickPick("", Bundle.NoKeys(), Collections.emptyList(), false);
            }
            throw new IllegalStateException("No data to create input"); // NOI18N
        }

        @Override
        public void setValue(String selected) {
            this.selected = keys.get(selected);
        }

        @Override
        public KeyItem getValue() {
            if (keys.size() == 1) {
                return keys.values().iterator().next();
            }
            return selected;
        }

    }

    class DatasourceNameStep extends AbstractStep<String> {
        private String selected;

        @Override
        public void prepare(ProgressHandle h) {
        }

        @Override
        public NotifyDescriptor createInput() {
            return new NotifyDescriptor.InputLine("DEFAULT", Bundle.DatasourceName()); //NOI18N
        }

        @Override
        public void setValue(String selected) {
            this.selected = selected;
        }

        @Override
        public String getValue() {
            return selected;
        }

        @Override
        public boolean onlyOneChoice() {
            return false;
        }

    }

    class OverwriteStep extends AbstractStep<Boolean> {
        private Set<String> dsNames;
        private String choice;
        private Lookup lookup;
        private String dsName;

        @Override
        public void prepare(ProgressHandle h) {
            dsName = (String) values.getValueForStep(DatasourceNameStep.class);
            VaultItem vault = (VaultItem) values.getValueForStep(VaultStep.class);
            if (dsName == null || dsName.isEmpty()) {
                return;
            }
            List<SecretItem> secrets = SecretNode.getSecrets().apply(vault);
            this.dsNames = secrets.stream()
                    .map(s -> extractDatasourceName(s.getName()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        @Override
        public NotifyDescriptor createInput() {
            if (dsName == null || dsName.isEmpty()) {
                return new NotifyDescriptor.QuickPick("", Bundle.DatasourceEmpty(), Collections.emptyList(), false);
            }
            List<Item> yesNo = new ArrayList();
            yesNo.add(new Item(Bundle.AddVersion(), ""));
            yesNo.add(new Item(Bundle.Cancel(), ""));
            return new NotifyDescriptor.QuickPick("", Bundle.SecretExists(dsName), yesNo, false);
        }

        @Override
        public void setValue(String choice) {
            this.choice = choice;
        }

        @Override
        public Boolean getValue() {
            return Bundle.AddVersion().equals(choice) || onlyOneChoice();
        }

        @Override
        public boolean onlyOneChoice() {
            return dsNames != null && !dsNames.contains(dsName);
        }

    }

    class PasswordStep extends AbstractStep<String> {
        private boolean ask;
        private String password;

        @Override
        public void prepare(ProgressHandle h) {
            password = context.getPassword();
            ask = password == null || password.isEmpty();
        }

        @Override
        public NotifyDescriptor createInput() {
            return new NotifyDescriptor.PasswordLine("DEFAULT", Bundle.Password(context.getUser())); //NOI18N
        }

        @Override
        public boolean onlyOneChoice() {
            return !ask;
        }

        @Override
        public void setValue(String password) {
            this.password = password;
        }

        @Override
        public String getValue() {
            return password;
        }
    }

//    public static final class Result {
//        VaultItem vault;
//        KeyItem key;
//        String datasourceName;
//        String password;
//        DevopsProjectItem project;
//        private boolean update;
//    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        NextStepProvider nsProvider = NextStepProvider.builder()
                .stepForClass(CompartmentStep.class, (s) -> new VaultStep())
                .stepForClass(DevopsStep.class, (s) -> new ProjectStep())
                .stepForClass(KeyStep.class, (s) -> new DatasourceNameStep())
                .stepForClass(DatasourceNameStep.class, (s) -> new OverwriteStep())
                .stepForClass(OverwriteStep.class, (s) -> new PasswordStep())
                .stepForClass(PasswordStep.class, (s) -> new DevopsStep())
                .build();
        Lookup lookup = Lookups.fixed(nsProvider);
        Steps.getDefault().executeMultistep(new TenancyStep(), lookup).thenAccept(r -> {
            Values vals = lookup.lookup(Values.class);
            Project project = (Project) vals.getValueForStep(ProjectStep.class);
            DevopsProjectItem devopsProject = (DevopsProjectItem) vals.getValueForStep(DevopsStep.class);
            String datasourceName = (String) vals.getValueForStep(DatasourceNameStep.class);
            VaultItem vault = (VaultItem) vals.getValueForStep(VaultStep.class);
            KeyItem key = (KeyItem) vals.getValueForStep(KeyStep.class);
            String password = (String) vals.getValueForStep(PasswordStep.class);
            if (datasourceName == null || datasourceName.isEmpty()) {
                    NotifyDescriptor.Message msg = new NotifyDescriptor.Message(Bundle.DatasourceEmpty());
                    DialogDisplayer.getDefault().notify(msg);
                    return;
                }
            addDbConnectionToVault(vault, key, project, devopsProject, datasourceName, password);
        });
    }

    private void addDbConnectionToVault(VaultItem vault, KeyItem key, Project project, DevopsProjectItem devopsProject, String datasourceName, String password) {
        ProgressHandle h = ProgressHandle.createHandle(Bundle.UpdatingVault(vault.getName()));
        h.start();
        h.progress(Bundle.ReadingSecrets());
           
        try {
            VaultsClient client = VaultsClient.builder().build(getDefault().getActiveProfile().getConfigProvider());

            ListSecretsRequest listSecretsRequest = ListSecretsRequest.builder()
                    .compartmentId(vault.getCompartmentId())
                    .vaultId(vault.getKey().getValue())
                    .limit(88)
                    .build();

            ListSecretsResponse secrets = client.listSecrets(listSecretsRequest);

        Map<String, String> existingSecrets = secrets.getItems().stream()
                .collect(Collectors.toMap(s -> s.getSecretName(), s -> s.getId()));

            Map<String, String> values = new HashMap<String, String>() {
                {
                    put("Username", context.getUser()); //NOI18N
                    put("Password", password); //NOI18N
                    put("OCID", (String) context.getConnectionProperties().get("OCID")); //NOI18N
                    put("CompartmentOCID", (String) context.getConnectionProperties().get("CompartmentOCID")); //NOI18N
                    put("wallet_Password", UUID.randomUUID().toString()); //NOI18N
                }
            };

            for (Entry<String, String> entry : values.entrySet()) {
                String secretName = "DATASOURCES_" + datasourceName + "_" + entry.getKey().toUpperCase(); //NOI18N
                String base64Content = Base64.getEncoder().encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8));

                SecretContentDetails contentDetails = Base64SecretContentDetails.builder()
                        .content(base64Content)
                        .stage(SecretContentDetails.Stage.Current).build();
                if (existingSecrets.containsKey(secretName)) {
                    h.progress(Bundle.UpdatingSecret(secretName));
                    UpdateSecretDetails updateSecretDetails = UpdateSecretDetails.builder()
                            .secretContent(contentDetails)
                            .build();
                    UpdateSecretRequest request = UpdateSecretRequest.builder()
                            .secretId(existingSecrets.get(secretName))
                            .updateSecretDetails(updateSecretDetails)
                            .build();
                    try { 
                        UpdateSecretResponse response = client.updateSecret(request);
                    } catch (BmcException ex) {
                        // Update fails if the new value is same as the current one. It is safe to ignore
                        LOG.log(Level.WARNING, "Update of secret failed", ex);
                    }
                } else {
                    h.progress(Bundle.CreatingSecret(secretName));
                    CreateSecretDetails createDetails = CreateSecretDetails.builder()
                            .secretName(secretName)
                            .secretContent(contentDetails)
                            .secretRules(new ArrayList<>(Arrays.asList(SecretReuseRule.builder()
                                    .isEnforcedOnDeletedSecretVersions(false).build())))
                            .compartmentId(vault.getCompartmentId())
                            .vaultId(vault.getKey().getValue())
                            .keyId(key.getKey().getValue())
                            .build();
                    CreateSecretRequest request = CreateSecretRequest
                            .builder()
                            .createSecretDetails(createDetails)
                            .build();
                    client.createSecret(request);
                }
            }
            
            // Add Vault dependency to the project
            try {
                DependencyUtils.addDependency(project, "io.micronaut.oraclecloud", "micronaut-oraclecloud-vault");
            } catch (IllegalStateException e) {
                LOG.log(Level.INFO, "Unable to add Vault dependency", e);
            }
            
            // Add Vault to the ConfigMap artifact
            DevopsClient devopsClient = DevopsClient.builder().build(OCIManager.getDefault().getActiveProfile().getConfigProvider());
            ListDeployArtifactsRequest request = ListDeployArtifactsRequest.builder()
                    .projectId(devopsProject.getKey().getValue()).build();
            ListDeployArtifactsResponse response = devopsClient.listDeployArtifacts(request);
            List<DeployArtifactSummary> artifacts = response.getDeployArtifactCollection().getItems();
            boolean found = false;
            for (DeployArtifactSummary artifact : artifacts) {
                if ((devopsProject.getName() + "_oke_configmap").equals(artifact.getDisplayName())) { //NOI18N
                    h.progress("updating  " + devopsProject.getName() + "_oke_configmap"); //NOI18N
                    found = true;
                    GetDeployArtifactRequest artRequest = GetDeployArtifactRequest.builder().deployArtifactId(artifact.getId()).build();
                    GetDeployArtifactResponse artResponse = devopsClient.getDeployArtifact(artRequest);
                    DeployArtifactSource source = artResponse.getDeployArtifact().getDeployArtifactSource();
                    if (source instanceof InlineDeployArtifactSource) {
                        byte[] content = ((InlineDeployArtifactSource) source).getBase64EncodedContent();
                        String srcString = updateProperties(new String(content, StandardCharsets.UTF_8),
                                vault.getCompartmentId(), vault.getKey().getValue(), datasourceName);
                        byte[] base64Content = Base64.getEncoder().encode(srcString.getBytes(StandardCharsets.UTF_8));
                        DeployArtifactSource updatedSource = InlineDeployArtifactSource.builder()
                                .base64EncodedContent(base64Content).build();
                        UpdateDeployArtifactDetails updateArtifactDetails = UpdateDeployArtifactDetails.builder()
                                .deployArtifactSource(updatedSource)
                                .build();
                        UpdateDeployArtifactRequest updateArtifactRequest = UpdateDeployArtifactRequest.builder()
                                .updateDeployArtifactDetails(updateArtifactDetails)
                                .deployArtifactId(artifact.getId())
                                .build();
                        devopsClient.updateDeployArtifact(updateArtifactRequest);
                    }
                }
            }
            if (!found) {
                NotifyDescriptor.Message msg = new NotifyDescriptor.Message(Bundle.NoConfigMap(devopsProject.getName()), NotifyDescriptor.WARNING_MESSAGE);
                DialogDisplayer.getDefault().notify(msg);
            }
            NotifyDescriptor.Message msg = new NotifyDescriptor.Message(Bundle.SecretsCreated());
            DialogDisplayer.getDefault().notify(msg);
        } catch(ThreadDeath e) {
            throw e;
        } catch (Throwable e) {
            h.finish();
            NotifyDescriptor.Message msg = new NotifyDescriptor.Message(e.getMessage(), NotifyDescriptor.WARNING_MESSAGE);
            DialogDisplayer.getDefault().notify(msg);
        } finally {
            h.finish();
        }
    }

    protected static String updateProperties(String configmap, String compartmentOcid, String vaultOcid, String datasourceName) {
        StringWriter output = new StringWriter();
        String[] lines = configmap.split("\n");
        int previousIndent = 0;
        Map<Integer, String> path = new LinkedHashMap<>();
        String propertiesName = null;
        Map<String, String> properties = new LinkedHashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("#") || line.isEmpty()) {
                output.append(line);
                output.append("\n");
                continue;
            }
            int indent = 0;
            while (line.charAt(indent) == ' ') {
                indent++;
            }
            if (previousIndent > indent || (propertiesName != null && !line.contains("="))) {
                final int f = indent;
                path.entrySet().removeIf(entry -> entry.getKey() >= f);
                if (propertiesName != null) {
                    int propIndent = previousIndent;
                    if (properties.isEmpty()) {
                        propIndent = indent + 2;
                    }
                    output.append(
                            formatProperties(propertiesName, properties, propIndent, compartmentOcid, vaultOcid, datasourceName));

                    properties.clear();
                }
                propertiesName = null;
                if (line.trim().equals("---")) { //NOI18N
                    output.append(line);
                    output.append("\n");
                    continue;
                }
            }
            if (propertiesName == null) {
                if (line.indexOf(':') < 0) {
                    throw new IllegalStateException("Invalid ConfigMap format"); //NOI18N
                }
                String k = line.substring(0, line.indexOf(':')).trim();
                String v = line.substring(line.indexOf(':') + 1).trim();
                if (k == null) {
                    throw new IllegalStateException();
                }

                path.put(indent, k);
                output.append(line);
                output.append("\n");
                if (v.trim().equals("|")) {
                    propertiesName = k;
                    continue;
                }
            }
            if (propertiesName != null && line.contains("=")) {
                properties.put(line.substring(0, line.indexOf('=')).trim(),
                        line.substring(line.indexOf('=') + 1).trim());
            }

            previousIndent = indent;
        }
        output.append(
                formatProperties(propertiesName, properties, previousIndent, compartmentOcid, vaultOcid, datasourceName));

        return output.toString();
    }

    private static String formatProperties(String proprtiesName, Map<String, String> prop, int indent, String compartmentId, String vaultId, String datasourceName) {
        StringBuilder output = new StringBuilder();
        if (proprtiesName.startsWith("bootstrap")) { // NOI18N
            prop.entrySet().removeIf(entry -> ((String) entry.getKey()).startsWith("oci.vault.vaults")); // NOI18N
            prop.put("oci.config.instance-principal.enabled", "true"); // NOI18N
            prop.put("micronaut.config-client.enabled", "true"); // NOI18N
            prop.put("oci.vault.config.enabled", "true"); // NOI18N
            prop.put("oci.vault.vaults[0].ocid", vaultId); // NOI18N
            prop.put("oci.vault.vaults[0].compartment-ocid", compartmentId); // NOI18N
        } else if (proprtiesName.startsWith("application")) { // NOI18N
            prop.put("datasources.default.dialect", "ORACLE"); // NOI18N
            prop.put("datasources.default.ocid", "${DATASOURCES_" + datasourceName + "_OCID}"); // NOI18N
            prop.put("datasources.default.walletPassword", "${DATASOURCES_" + datasourceName + "_WALLET_PASSWORD}"); // NOI18N
            prop.put("datasources.default.username", "${DATASOURCES_" + datasourceName + "_USERNAME}"); // NOI18N
            prop.put("datasources.default.password", "${DATASOURCES_" + datasourceName + "_PASSWORD}"); // NOI18N
        }
        for (Entry<String, String> entry : prop.entrySet()) {
            output.append(new String(new char[indent]).replace('\0', ' '));
            output.append(entry.getKey());
            output.append("=");
            output.append(entry.getValue());
            output.append("\n");
        }
        return output.toString();
    }

    private static <T extends OCIItem> NotifyDescriptor.QuickPick createQuickPick(Map<String, T> ociItems, String title) {

        List<NotifyDescriptor.QuickPick.Item> items = ociItems.entrySet().stream()
                .map(entry -> new NotifyDescriptor.QuickPick.Item(entry.getKey(), entry.getValue().getDescription()))
                .collect(Collectors.toList());
        return new NotifyDescriptor.QuickPick(title, title, items, false);
    }

    protected static Map<String, KeyItem> getKeys(OCIItem parent) {
        Map<String, KeyItem> items = new HashMap<>();
        try {
            if (parent instanceof VaultItem) {
                KeyNode.getKeys().apply((VaultItem) parent).forEach(key -> items.put(key.getName(), key));
            }
        } catch (BmcException e) {
            LOG.log(Level.SEVERE, "Unable to load key list", e); //NOI18N
        }
        return items;
    }

    static Pattern p = Pattern.compile("[A-Z]*_([a-zA-Z0-9]*)_[A-Z]*"); //NOI18N

    protected static String extractDatasourceName(String value) {
        Matcher m = p.matcher(value);
        if (m.matches()) {
            return m.group(1);
        }
        return null;
    }
}
