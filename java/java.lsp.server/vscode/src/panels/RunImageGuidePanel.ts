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

import * as vscode from "vscode";
import * as path from "path";
import * as fs from "fs";
import * as Handlebars from "handlebars";
import { promisify } from "util";
import * as os from "os";
import { toggleGuideFor, shouldShowGuideFor, removeGuidePropertiesFromState, NotificationActionType } from "./guidesUtil";

const readFile = promisify(fs.readFile);

export class RunImageGuidePanel {
    public static currentPanel: RunImageGuidePanel | undefined;
    public static readonly viewType: string = "runImageGuide";

    private static readonly actionType: NotificationActionType = 'run-container-image';
    private static readonly webviewsFolder: string = "webviews";
    private static readonly scriptsFolder: string = "scripts";
    private static readonly stylesFolder: string = "styles";

    private readonly panel: vscode.WebviewPanel;
    private disposables: vscode.Disposable[] = [];

    public static createOrShow(context: vscode.ExtensionContext) {
        if (RunImageGuidePanel.currentPanel) {
            RunImageGuidePanel.currentPanel.panel.reveal();
            return;
        }

        RunImageGuidePanel.currentPanel = new RunImageGuidePanel(context);
    }

    private constructor(context: vscode.ExtensionContext) {
        this.panel = vscode.window.createWebviewPanel(
            RunImageGuidePanel.viewType,
            "Oracle Cloud Assets",
            { viewColumn: vscode.ViewColumn.One, preserveFocus: true },
            {
                enableCommandUris: true,
                enableScripts: true,
                localResourceRoots: [vscode.Uri.file(path.join(context.extensionPath, RunImageGuidePanel.webviewsFolder))],
            }
        );
        const iconPath: vscode.Uri = vscode.Uri.file(path.join(context.extensionPath, "images", "Apache_NetBeans_Logo.png"));
        this.panel.iconPath = {
            light: iconPath,
            dark: iconPath,
        };

        this.setHtml(context);

        this.panel.onDidDispose(
            () => {
                this.dispose();
                removeGuidePropertiesFromState(context);
            },
            null,
            this.disposables
        );

        this.panel.onDidChangeViewState(
            () => {
                if (this.panel.visible) {
                    this.setHtml(context);
                }
            },
            null,
            this.disposables
        );

        this.panel.webview.onDidReceiveMessage(
            (message) => {
                if (message.command === "showGuide") {
                    const ocid: string = context.globalState.get("guide-ocid") || "";
                    toggleGuideFor(RunImageGuidePanel.actionType, ocid);
                }
            },
            undefined,
            this.disposables
        );
    }

    private async setHtml(context: vscode.ExtensionContext) {
        let templateFile = path.resolve(__dirname, "../../webviews/run-image-guide.handlebars");
        templateFile = await readFile(templateFile, "utf-8");
        const template = Handlebars.compile(templateFile);
        const ocid: string | undefined = context.globalState.get<string>("guide-ocid");

        this.panel.webview.html = template({
            cspSource: this.panel.webview.cspSource,
            publicIp: context.globalState.get<string>("guide-publicIp"),
            homeDir: os.homedir(),
            showGuide: shouldShowGuideFor(RunImageGuidePanel.actionType, ocid) ? "checked" : "",
            cssUri: this.panel.webview.asWebviewUri(
                vscode.Uri.file(path.join(context.extensionPath, RunImageGuidePanel.webviewsFolder, RunImageGuidePanel.stylesFolder, "guide.css"))
            ),
            javascriptUri: this.panel.webview.asWebviewUri(
                vscode.Uri.file(path.join(context.extensionPath, RunImageGuidePanel.webviewsFolder, RunImageGuidePanel.scriptsFolder, "guide.js"))
            ),
        });
    }

    public dispose() {
        RunImageGuidePanel.currentPanel = undefined;

        this.panel.dispose();
        while (this.disposables.length) {
            const x = this.disposables.pop();
            if (x) {
                x.dispose();
            }
        }
    }
}
