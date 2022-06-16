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

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Item;

import java.io.Serializable;

/**
 * Extension point for providing a new implementation of item storage that plugins can use to store data associated
 * with an item in whatever storage mechanism the storage implementation provides.
 *
 * @author Peter Hayes
 */
public abstract class ItemStorage<T extends ObjectPath> extends AbstractDescribableImpl<ItemStorage<T>> implements ExtensionPoint, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Given an item and path, return an ObjectPath implementation for it
     *
     * @param item The item to associate the path with
     * @param path The path scoped by the item
     * @return The ObjectPath to act upon that path
     */
    public abstract T getObjectPath(Item item, String path);

    /**
     * Given an item, a path and a branch, return an ObjectPath implementation for it
     *
     * @param item The item to associate the path with
     * @param path The path scoped by the item
     * @param branch The branch for which to get the object path for
     * @return The ObjectPath to act upon that path
     */
    public abstract T getObjectPathForBranch(Item item, String path, String branch);
}
