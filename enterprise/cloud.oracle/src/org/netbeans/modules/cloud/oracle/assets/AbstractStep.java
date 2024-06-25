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
package org.netbeans.modules.cloud.oracle.assets;

import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 *
 * @author Jan Horvath
 */
@NbBundle.Messages({
    "LoadingItems=Loading items for the next step"
})
public abstract class AbstractStep<U> implements Step<U> {
    private Lookup lookup;
    protected Steps.Values values;
    

    @Override
    public final Step prepare(Lookup lookup) {
        this.lookup = lookup;
        values = lookup.lookup(Steps.Values.class);
        ProgressHandle h = ProgressHandle.createHandle(Bundle.LoadingItems());
        h.start();
        try {
            prepare(h);
        } finally {
            h.finish();
        }
        return this;
    }
    
    public void prepare(ProgressHandle handle) {
    }
    
    @Override
    public Step getNext() {
        Steps.NextStepProvider nsProvider = lookup.lookup(Steps.NextStepProvider.class);
        if (nsProvider != null) {
            Step ns = nsProvider.nextStepFor(this);
            if (ns != null) {
                return ns.prepare(lookup);
            }
        } 
        return null;
    }
}
