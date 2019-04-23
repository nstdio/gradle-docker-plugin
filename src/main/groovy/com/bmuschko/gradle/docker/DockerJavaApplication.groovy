/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Nested

/**
 * The extension for configuring a Java application via the {@link DockerJavaApplicationPlugin}.
 * <p>
 * Enhances the extension {@link DockerExtension} as child DSL element.
 * <p>
 * The following example demonstrates the use of the extension in a build script using the Groovy DSL:
 * <pre>
 * docker {
 *     javaApplication {
 *         baseImage = 'dockerfile/java:openjdk-7-jre'
 *         maintainer = 'Benjamin Muschko "benjamin.muschko@gmail.com"'
 *         ports = [9090, 5701]
 *         tag = 'jettyapp:1.115'
 *         jvmArgs = ['-Xms256m', '-Xmx2048m']
 *    }
 * }
 * </pre>
 */
@CompileStatic
class DockerJavaApplication {

    /**
     * The Docker base image used for Java application.
     * <p>
     * Defaults to {@code openjdk:jre-alpine}.
     */
    final Property<String> baseImage

    /**
     * The maintainer of the image.
     * <p>
     * Defaults to the value of the system property {@code user.name}.
     */
    final Property<String> maintainer

    /**
     * The Docker image exposed ports.
     * <p>
     * Defaults to {@code [8080]}.
     */
    final ListProperty<Integer> ports

    /**
     * The tag used for the Docker image.
     * <p>
     * Defaults to {@code <project.group>/<applicationName>:<project.version>}.
     */
    final Property<String> tag

    /**
     * The JVM arguments used to start the Java program.
     * <p>
     * Defaults to {@code []}.
     *
     * @since 4.8.0
     */
    final ListProperty<String> jvmArgs

    private final CompositeExecInstruction compositeExecInstruction

    DockerJavaApplication(ObjectFactory objectFactory) {
        baseImage = objectFactory.property(String)
        baseImage.set('openjdk:jre-alpine')
        maintainer = objectFactory.property(String)
        maintainer.set(System.getProperty('user.name'))
        ports = objectFactory.listProperty(Integer)
        ports.set([8080])
        tag = objectFactory.property(String)
        jvmArgs = objectFactory.listProperty(String).empty()
        compositeExecInstruction = new CompositeExecInstruction(objectFactory)
    }

    /**
     * Specifies the definitive ENTRYPOINT and/or CMD Dockerfile instructions.
     *
     * @param action Action
     * @return The instruction
     */
    CompositeExecInstruction exec(Action<CompositeExecInstruction> action) {
        compositeExecInstruction.clear()
        action.execute(compositeExecInstruction)
        return compositeExecInstruction
    }

    /**
     * Returns the definitive ENTRYPOINT and/or CMD Dockerfile instructions.
     *
     * @return The instruction
     */
    CompositeExecInstruction getExecInstruction() {
        compositeExecInstruction
    }

    /**
     * Helper Instruction to allow customizing generated ENTRYPOINT/CMD.
     */
    static class CompositeExecInstruction implements Dockerfile.Instruction {
        private final ListProperty<Dockerfile.Instruction> instructions

        CompositeExecInstruction(ObjectFactory objectFactory) {
            instructions = objectFactory.listProperty(Dockerfile.Instruction)
        }

        @Nested
        ListProperty<Dockerfile.Instruction> getInstructions() {
            instructions
        }

        void clear() {
            instructions.empty()
        }

        /**
         * Always returns an empty String as this type modifies existing instructions instead of creating new ones.
         * <p>
         * TODO: This data structure is awkward as it doesn't fit the contract of the interface. The capability should be implemented in a more intuitive way.
         *
         * @return A blank String
         */
        @Override
        String getKeyword() { '' }

        /**
         * Returns all instructions in the Dockerfile.
         * <p>
         * TODO: This data structure is awkward as it doesn't fit the contract of the interface. The capability should be implemented in a more intuitive way.
         *
         * @return All instructions
         */
        @Override
        @CompileStatic(TypeCheckingMode.SKIP)
        String getText() {
            instructions.get()
                .collect { it.text }
                .join(System.getProperty('line.separator'))
        }

        /**
         * Overrides the default entry point generated by the plugin.
         * <p>
         * By default the entry point is {@code ENTRYPOINT ["java", "-cp", "/app/resources:/app/classes:/app/libs/*", <main-class-name>]}.
         *
         * @param entryPoint Entry point
         * @see #entryPoint(Provider)
         */
        void entryPoint(String... entryPoint) {
            addInstruction(new Dockerfile.EntryPointInstruction(entryPoint))
        }

        /**
         * Overrides the default entry point generated by the plugin.
         * <p>
         * By default the entry point is {@code ENTRYPOINT ["java", "-cp", "/app/resources:/app/classes:/app/libs/*", <main-class-name>]}.
         *
         * @param provider Entry point as provider
         * @see #entryPoint(java.lang.String[])
         */
        void entryPoint(Provider<List<String>> provider) {
            addInstruction(new Dockerfile.EntryPointInstruction(provider))
        }

        /**
         * Specifies the command generated by the plugin.
         * <p>
         * By default the plugin doesn't generate a command instruction.
         *
         * @param command Command
         * @see #defaultCommand(Provider)
         */
        void defaultCommand(String... command) {
            addInstruction(new Dockerfile.DefaultCommandInstruction(command))
        }

        /**
         * Specifies the command generated by the plugin.
         * <p>
         * By default the plugin doesn't generate a command instruction.
         *
         * @param provider Command as Provider
         * @see #defaultCommand(java.lang.String[])
         */
        void defaultCommand(Provider<List<String>> provider) {
            addInstruction(new Dockerfile.DefaultCommandInstruction(provider))
        }

        private void addInstruction(Dockerfile.Instruction instruction) {
            instructions.add(instruction)
        }
    }
}
