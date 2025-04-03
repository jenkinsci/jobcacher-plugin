/*
 * The MIT License
 *
 * Copyright 2016 Peter Hayes.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.itemstorage;

import hudson.Extension;
import hudson.model.PersistentDescriptor;
import java.util.Collections;
import java.util.List;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.plugins.itemstorage.local.LocalItemStorage;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * This class lets users specify globally which implementation of Item Storage should be used on this instance.
 *
 * @author Peter Hayes
 */
@Extension
public class GlobalItemStorage extends GlobalConfiguration implements PersistentDescriptor {
    private ItemStorage<?> storage = new LocalItemStorage();

    public static GlobalItemStorage get() {
        return GlobalConfiguration.all().get(GlobalItemStorage.class);
    }

    public ItemStorage<?> getStorage() {
        return storage;
    }

    public void setStorage(ItemStorage<?> storage) {
        this.storage = storage;
        save();
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<ItemStorageDescriptor> getStorageDescriptors() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            return jenkins.getDescriptorList(ItemStorage.class);
        } else {
            return Collections.emptyList();
        }
    }
}
