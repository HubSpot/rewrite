/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.maven;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.xml.Assertions.xml;

public class AssertionsTest implements RewriteTest {
    private static final AtomicInteger xmlCount = new AtomicInteger();

    @BeforeEach
    void reset() {
        xmlCount.set(0);
    }

    @Test
    void xmlAndPomXmlUseCorrectParserWhenPomXmlIsFirst() {
        rewriteRun(
          spec -> spec.recipe(new MavenOnlyRecipe()),
          pomXml("""
                        <project>
                            <groupId>org.openrewrite</groupId>
                            <artifactId>test</artifactId>
                            <version>1.0.0</version>
                            <dependencies>
                                <dependency>
                                    <groupId>com.fasterxml.jackson</groupId>
                                    <artifactId>jackson-base</artifactId>
                                    <version>2.14.2</version>
                                </dependency>
                            </dependencies>
                        </project>
            """), xml("""
              <?xml version="1.0" encoding="UTF-8" ?>
              <suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
              </suppressions>""",
            spec -> spec.path("suppressions.xml"))
        );
        assertThat(xmlCount.get()).isEqualTo(2);
    }

    @Test
    void xmlAndPomXmlUseCorrectParserWhenPomXmlIsLast() {
        rewriteRun(
          spec -> spec.recipe(new MavenOnlyRecipe()),
          xml("""
              <?xml version="1.0" encoding="UTF-8" ?>
              <suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
              </suppressions>""",
            spec -> spec.path("suppressions.xml")),
          pomXml("""
                        <project>
                            <groupId>org.openrewrite</groupId>
                            <artifactId>test</artifactId>
                            <version>1.0.0</version>
                            <dependencies>
                                <dependency>
                                    <groupId>com.fasterxml.jackson</groupId>
                                    <artifactId>jackson-base</artifactId>
                                    <version>2.14.2</version>
                                </dependency>
                            </dependencies>
                        </project>
            """)
        );
        assertThat(xmlCount.get()).isEqualTo(2);
    }

    private static class MavenOnlyRecipe extends Recipe {
        @Override
        public String getDisplayName() {
            return "Fail if run on not-maven";
        }

        @Override
        public String getDescription() {
            return "Super description.";
        }

        private MavenOnlyRecipe() {
            doNext(new NonMavenRecipe());
        }

        @Override
        protected TreeVisitor<?, ExecutionContext> getVisitor() {
            return new MavenIsoVisitor<>() {
                @Nullable
                private String filename;

                @Override
                @Nullable
                public Xml visitSourceFile(SourceFile sourceFile, ExecutionContext executionContext) {
                    Xml xml = super.visitSourceFile(sourceFile, executionContext);
                    filename = sourceFile.getSourcePath().getFileName().toString();
                    return xml;
                }

                @Override
                public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                    assertThat(filename).isEqualTo("pom.xml");
                    return super.visitTag(tag, executionContext);
                }
            };
        }
    }

    private static class NonMavenRecipe extends Recipe {
        @Override
        public String getDisplayName() {
            return "Fail if run on maven";
        }

        @Override
        public String getDescription() {
            return "Super description.";
        }

        @Override
        protected TreeVisitor<?, ExecutionContext> getVisitor() {
            return new XmlIsoVisitor<>() {
                @Override
                public @Nullable Xml visitSourceFile(SourceFile sourceFile, ExecutionContext executionContext) {
                    xmlCount.incrementAndGet();
                    return super.visitSourceFile(sourceFile, executionContext);
                }
            };
        }
    }
}