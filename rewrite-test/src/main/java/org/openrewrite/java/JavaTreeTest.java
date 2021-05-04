/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java;

import org.junit.jupiter.api.BeforeEach;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.TreeSerializer;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

import java.util.Arrays;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public interface JavaTreeTest {
    default ExecutionContext getExecutionContext() {
        return new InMemoryExecutionContext(t -> fail("Failed to parse", t));
    }

    @BeforeEach
    default void beforeEachJavaTreeTest() {
        J.clearCaches();
    }

    default void assertParsePrintAndProcess(JavaParser parser, NestingLevel nestingLevel, String code,
                                            String... imports) {
        String source = Arrays.stream(imports).map(i -> "import " + i + ";").collect(joining(""));

        switch (nestingLevel) {
            case Block:
                source = source + "class A" + System.nanoTime() + "{{\n" + code + "\n}}";
                break;
            case Class:
                source = source + "class A" + System.nanoTime() + "{\n" + code + "\n}";
                break;
            case CompilationUnit:
                source = source + "/*<START>*/\n" + code;
                break;
        }

        JavaSourceFile cu = parser.parse(getExecutionContext(), source).iterator().next();

        J processed = new JavaVisitor<>().visit(cu, new Object());
        assertThat(processed).as("Processing is idempotent").isSameAs(cu);

        TreeSerializer<JavaSourceFile> treeSerializer = new TreeSerializer<>();
        JavaSourceFile roundTripCu = treeSerializer.read(treeSerializer.write(cu));

        assertThat(JavaParserTestUtil.print(nestingLevel, cu))
                .as("Source code is printed the same after parsing")
                .isEqualTo(StringUtils.trimIndent(code));

        assertThat(JavaParserTestUtil.print(nestingLevel, roundTripCu))
                .as("Source code is printed the same after round trip serialization")
                .isEqualTo(StringUtils.trimIndent(code));
    }

    enum NestingLevel {
        Block,
        Class,
        CompilationUnit
    }
}

class JavaParserTestUtil {
    static String print(JavaTreeTest.NestingLevel nestingLevel, JavaSourceFile cu) {
        String printed;
        switch (nestingLevel) {
            case Block:
                printed = cu.getClasses().iterator().next().getBody().getStatements().iterator().next().printTrimmed();
                printed = printed.substring(1, printed.length() - 1);
                break;
            case Class:
                printed = cu.getClasses().iterator().next().getBody().printTrimmed();
                printed = printed.substring(1, printed.length() - 1);
                break;
            case CompilationUnit:
            default:
                printed = cu.printTrimmed();
                printed = printed.substring(printed.indexOf("/*<START>*/") + "/*<START>*/".length());
                break;
        }
        return StringUtils.trimIndent(printed);
    }
}
