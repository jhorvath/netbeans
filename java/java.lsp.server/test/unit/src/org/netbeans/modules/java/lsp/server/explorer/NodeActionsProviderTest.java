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
package org.netbeans.modules.java.lsp.server.explorer;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import org.eclipse.lsp4j.LogTraceParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.Test;
import org.netbeans.modules.java.lsp.server.TestCodeLanguageClient;
import org.openide.awt.ActionID;
import org.openide.filesystems.Repository;
import org.openide.filesystems.XMLFileSystem;
import org.openide.util.test.MockLookup;

/**
 *
 * @author Jan Horvath
 */
public class NodeActionsProviderTest {
    
    /**
     * Test of processCommand method, of class NodeActionsProvider.
     */
    @Test
    public void testProcessCommand() throws IOException, PropertyVetoException, InterruptedException, InvocationTargetException {
        System.setProperty("java.awt.headless", Boolean.TRUE.toString());
        XMLFileSystem layersFs = new XMLFileSystem();
        layersFs.setXmlUrls(new URL[] {
            getClass().getClassLoader().getResource("org/netbeans/modules/java/lsp/server/explorer/test-layer.xml")});
        try {
            MockLookup.setInstances(
                          new Repository.LocalProvider() {
                                @Override
                                public Repository getRepository() throws IOException {
                                      return new Repository(layersFs);
                                }
                          }
                    );
            NodeActionsProvider p = new NodeActionsProvider(Collections.EMPTY_SET);
            Map<String, String> m = new HashMap<String, String>();
            m.put("name", "test");
            m.put("id", "test1");
            
            // assert in GeneralAction.actionPerformed() fails
            SwingUtilities.invokeAndWait(() -> {
                p.invokeAction(new TestCodeLanguageClient() {
                    @Override
                    public boolean isRequestDispatcherThread() {
                        return super.isRequestDispatcherThread(); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
                    }

                    @Override
                    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
                        return super.registerCapability(params); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
                    }

                    @Override
                    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
                        return super.unregisterCapability(params); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
                    }

                    @Override
                    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
                        return super.showDocument(params); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
                    }

                    @Override
                    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
                        return super.workspaceFolders(); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
                    }

                    @Override
                    public void logTrace(LogTraceParams params) {
                        super.logTrace(params); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
                    }

                    @Override
                    public void setTrace(SetTraceParams params) {
                        super.setTrace(params); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
                    }

                    @Override
                    public CompletableFuture<Void> refreshSemanticTokens() {
                        return super.refreshSemanticTokens(); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
                    }

                    @Override
                    public CompletableFuture<Void> refreshCodeLenses() {
                        return super.refreshCodeLenses(); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
                    }
                }, "Tools", "testAction1", Collections.singletonList(m));
            });
        } finally {
            MockLookup.setInstances();
        }
    }
    
    @ActionID(
        category = "Tools",
        id = "testAction1"
    )
    class TestAction implements ActionListener {

        private final TestInfo context;

        public TestAction(TestInfo context) {
            this.context = context;
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }
    }
    
}
