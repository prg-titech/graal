/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Extract;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractBindsCacheNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractFieldNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractInLimitNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractMethodNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractMethodTwiceNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractNodeFieldNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractTransitiveCachedInAssumptionNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractTransitiveCachedInLimitNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractTransitiveCachedWithLibraryNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractTransitiveDynamicAndCachedNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractTransitiveDynamicNodeGen;
import com.oracle.truffle.api.dsl.test.ExtractExpressionTestFactory.ExtractTransitiveDynamicWithLibraryNodeGen;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings("unused")
public class ExtractExpressionTest extends AbstractPolyglotTest {

    static class TestObject {

        Object storage = new Object();
        Assumption assumption = Truffle.getRuntime().createAssumption();

        int counter = 0;

        int extract() {
            return counter++;
        }
    }

    /*
     * Test that storage field extraction works. Unfortunately here we cannot check how many times a
     * field is read.
     */
    @Test
    public void testExtractField() {
        ExtractFieldNode node = ExtractFieldNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);

        assertFails(() -> node.execute(new TestObject()), UnsupportedSpecializationException.class);
    }

    abstract static class ExtractFieldNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "storage == cachedStorage", limit = "1")
        Object s0(TestObject a0,
                        @Extract("a0.storage") Object storage,
                        @Cached("storage") Object cachedStorage) {
            assertSame(storage, cachedStorage);
            assertSame(a0.storage, storage);
            return a0;
        }
    }

    /*
     * This test verifies that the extract expression is invoked exactly once. Even though it is
     * used multiple times.
     */
    @Test
    public void testExtractMethod() {
        ExtractMethodNode node = ExtractMethodNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);

        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class ExtractMethodNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "counter == 0")
        Object s0(TestObject a0,
                        @Extract("a0.extract()") int counter,
                        @Cached("counter") int cachedCounter) {
            assertEquals(0, counter);
            assertEquals(0, cachedCounter);
            return a0;
        }
    }

    /*
     * This test verifies that the expression is invoked once even if used multiple times in the
     * guard.
     */
    @Test
    public void testExtractMethodTwice() {
        ExtractMethodTwiceNode node = ExtractMethodTwiceNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class ExtractMethodTwiceNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "counter == 0 || counter == 1")
        Object s0(TestObject a0,
                        @Extract("a0.extract()") int counter,
                        @Cached("counter") int cachedCounter) {
            assertEquals(0, cachedCounter);
            return a0;
        }
    }

    /*
     * Tests that if a guard binds a cached expression indirectly through extract it is not actually
     * executed multiple times.
     */
    @Test
    public void testExtractBindsCache() {
        ExtractBindsCacheNode node = ExtractBindsCacheNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        node.execute(o);
    }

    abstract static class ExtractBindsCacheNode extends Node {

        abstract Object execute(Object arg0);

        @ExpectError("The limit expression has no effect.%")
        // this guard is trivially true but the DSL cannot detect that yet.
        @Specialization(guards = "counter == cachedCounter", limit = "3")
        Object s0(TestObject a0,
                        @Cached("0") int cachedCounter,
                        @Extract("cachedCounter") int counter) {
            assertEquals(0, counter);
            assertEquals(0, cachedCounter);
            return a0;
        }
    }

    /*
     * This test verifies that an extract expression can be used in a limit expression if it only
     * binds cached values.
     */
    @Test
    public void testExtractInLimit() {
        ExtractInLimitNode node = ExtractInLimitNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class ExtractInLimitNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "counter == cachedCounter", limit = "limit")
        Object s0(TestObject a0,
                        @Extract("a0.extract()") int counter,
                        @Extract("2") int limit,
                        @Cached("counter") int cachedCounter) {
            assertEquals(cachedCounter, counter);
            assertEquals(cachedCounter, cachedCounter);
            return a0;
        }
    }

    /*
     * This test verifies that an extract expression can be chained transitively and its dynamic
     * nature is preserved.
     */
    @Test
    public void testExtractTransitiveDynamic() {
        ExtractTransitiveDynamicNode node = ExtractTransitiveDynamicNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class ExtractTransitiveDynamicNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "counter4 == cachedCounter", limit = "2")
        Object s0(TestObject a0,
                        @Extract("a0.extract()") int counter1,
                        @Extract("counter1") int counter2,
                        @Extract("counter2") int counter3,
                        @Extract("counter3") int counter4,
                        @Cached("counter3") int cachedCounter) {
            return a0;
        }
    }

    /*
     * This test verifies that an extract expression can be chained transitively and its dynamic
     * nature is preserved.
     */
    @Test
    public void testExtractTransitiveDynamicAndCachedNode() {
        ExtractTransitiveDynamicAndCachedNode node = ExtractTransitiveDynamicAndCachedNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class ExtractTransitiveDynamicAndCachedNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "counter1 == counter2", limit = "2")
        Object s0(TestObject a0,
                        @Extract("a0.extract()") int counter1,
                        @Cached("counter1") int cachedCounter,
                        @Extract("cachedCounter") int counter2) {
            return a0;
        }
    }

    /*
     * This test verifies that an extract expression can be chained transitively and its dynamic
     * nature is preserved when used with a library.
     */
    @Test
    public void testExtractTransitiveDynamicWithLibrary() {
        ExtractTransitiveDynamicWithLibraryNode node = ExtractTransitiveDynamicWithLibraryNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class ExtractTransitiveDynamicWithLibraryNode extends Node {

        abstract Object execute(Object arg0);

        // this should not trigger a warning
        @Specialization(guards = "counter2 < 2", limit = "3")
        Object s0(TestObject a0,
                        @Extract("a0.extract()") int counter1,
                        @Extract("counter1") int counter2,
                        @CachedLibrary("counter2") InteropLibrary lib) {
            assertTrue(lib.isNumber(counter2));
            return a0;
        }
    }

    /*
     * This test verifies that an extract expression can be chained transitively and its cached
     * nature is preserved when used with a library.
     */
    @Test
    public void testExtractTransitiveCachedWithLibrary() {
        ExtractTransitiveCachedWithLibraryNode node = ExtractTransitiveCachedWithLibraryNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
    }

    abstract static class ExtractTransitiveCachedWithLibraryNode extends Node {

        abstract Object execute(Object arg0);

        @ExpectError("The limit expression has no effect.%")
        @Specialization(limit = "3")
        Object s0(TestObject a0,
                        @Cached("a0.extract()") int cachedCounter,
                        @Extract("cachedCounter") int counter1,
                        @Extract("counter1") int counter2,
                        @CachedLibrary("counter2") InteropLibrary lib) {
            assertTrue(lib.isNumber(counter2));
            return a0;
        }
    }

    @Test
    public void testExtractTransitiveCachedInLimit() {
        ExtractTransitiveCachedInLimitNode node = ExtractTransitiveCachedInLimitNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    abstract static class ExtractTransitiveCachedInLimitNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "cachedExtract == extract", limit = "extractCachedLimit2")
        Object s0(TestObject a0,
                        @Extract("a0.extract()") int extract,
                        @Cached("extract") int cachedExtract,
                        @Cached("2") int cachedLimit,
                        @Extract("cachedLimit") int extractCachedLimit1,
                        @Extract("extractCachedLimit1") int extractCachedLimit2) {
            assertEquals(2, cachedLimit);
            return a0;
        }
    }

    @Test
    public void testExtractTransitiveCachedInAssumption() {
        ExtractTransitiveCachedInAssumptionNode node = ExtractTransitiveCachedInAssumptionNodeGen.create();
        TestObject o = new TestObject();
        node.execute(o);
        node.execute(o);
        o.assumption.invalidate();
        assertFails(() -> node.execute(o), UnsupportedSpecializationException.class);
    }

    /*
     * Test use of assumption is allowed for transitive extracted cached values.
     */
    abstract static class ExtractTransitiveCachedInAssumptionNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(assumptions = "extractAssumption2")
        Object s0(TestObject a0,
                        @Cached("a0.assumption") Assumption assumption,
                        @Extract("assumption") Assumption extractAssumption1,
                        @Extract("extractAssumption1") Assumption extractAssumption2) {
            return a0;
        }
    }

    @Test
    public void testExtractNodeField() {
        ExtractNodeFieldNode node = ExtractNodeFieldNodeGen.create(2);
        TestObject o = new TestObject();
        assertEquals(2, node.execute(o));
        assertEquals(2, node.execute(o));
    }

    @NodeField(name = "field0", type = int.class)
    abstract static class ExtractNodeFieldNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        int s0(TestObject a0,
                        @Extract("field0") int field) {
            return field;
        }
    }

    abstract static class ErrorUseInAssumptionsNode extends Node {

        abstract Object execute(Object arg0);

        @ExpectError("Assumption expressions must not bind dynamic parameter values.")
        @Specialization(assumptions = "assumption")
        Object s0(TestObject a0,
                        @Extract("a0.assumption") Assumption assumption) {
            return a0;
        }
    }

    abstract static class ErrorUseInLimitNode extends Node {

        abstract Object execute(Object arg0);

        @ExpectError("Limit expressions must not bind dynamic parameter values.")
        @Specialization(guards = "counter == cachedCounter", limit = "counter")
        Object s0(TestObject a0,
                        @Extract("a0.extract()") int counter,
                        @Cached("counter") int cachedCounter) {
            assertEquals(1, counter);
            assertEquals(1, cachedCounter);
            return a0;
        }
    }

    abstract static class ErrorCyclicUseNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(TestObject a0,
                        @Extract("a0.extract()") int extract,
                        @ExpectError("The initializer expression of parameter 'counter1' binds uninitialized parameter 'counter2. Reorder the parameters to resolve the problem.")//
                        @Extract("counter2") int counter1,
                        @Extract("counter1") int counter2) {
            return a0;
        }
    }

    abstract static class ErrorSyntaxNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(TestObject a0,
                        @ExpectError("Error parsing expression 'asdf32': asdf32 cannot be resolved.")//
                        @Extract("asdf32") int extract) {
            return a0;
        }
    }

    abstract static class ErrorEmptyNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(TestObject a0,
                        @ExpectError("Error parsing expression '': line 1:0 mismatched input '<EOF>'%")//
                        @Extract("") int extract) {
            return a0;
        }
    }

    /*
     * We don't want that static node constructors are looked up for extract annotations. This
     * should only work for Cached.
     */
    abstract static class ErrorUseNodeNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(TestObject a0,
                        @ExpectError("Error parsing expression 'create()': The method create is undefined for the enclosing scope.")//
                        @Extract("create()") ExtractFieldNode node) {
            return a0;
        }

    }

}
