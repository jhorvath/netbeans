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

import * as vscode from 'vscode';

export const GUIDE_NOTIFICATION_CONFIGURATION_KEY = 'guides.notified';

export type NotificationActionType = 'ssh-connection' | 'run-container-image'

type GuideNotification = {
    ocid: string;
    actionType: NotificationActionType;
}

export function removeGuidePropertiesFromState(context: vscode.ExtensionContext) {
    context.globalState.update("guide-publicIp", undefined);
    context.globalState.update("guide-ocid", undefined);
}

export function shouldShowGuideFor(actionType: NotificationActionType, ocid?: string) : boolean {
    const notifiedGuides: Array<GuideNotification> | undefined = vscode.workspace.getConfiguration('netbeans').get<Array<GuideNotification>>(GUIDE_NOTIFICATION_CONFIGURATION_KEY);
    return !notifiedGuides || !notifiedGuides.find((notified) => notified.ocid === ocid && notified.actionType === actionType)
}

export async function toggleGuideFor(actionType: NotificationActionType, ocid: string) {
    const notifiedGuides: Array<GuideNotification> = vscode.workspace.getConfiguration('netbeans').get<Array<GuideNotification>>(GUIDE_NOTIFICATION_CONFIGURATION_KEY) || [];
    const notifiedForOcid = notifiedGuides.find((notified) => notified.ocid === ocid && notified.actionType === actionType)
    if (notifiedForOcid) {
        try {
            await vscode.workspace.getConfiguration('netbeans').update(GUIDE_NOTIFICATION_CONFIGURATION_KEY,
                notifiedGuides.filter((notified) => notified.ocid !== ocid), true)            
        } catch (err) {
            vscode.window.showErrorMessage(`Failed to update property: netbeans.${GUIDE_NOTIFICATION_CONFIGURATION_KEY}, ${err}`);
        }
    } else {
        notifiedGuides.push({ocid, actionType});
        try {
            await vscode.workspace.getConfiguration('netbeans').update(GUIDE_NOTIFICATION_CONFIGURATION_KEY, [...notifiedGuides], true)     
        } catch (err) {
            vscode.window.showErrorMessage(`Failed to update property: netbeans.${GUIDE_NOTIFICATION_CONFIGURATION_KEY}, ${err}`);
        }
    }
}
