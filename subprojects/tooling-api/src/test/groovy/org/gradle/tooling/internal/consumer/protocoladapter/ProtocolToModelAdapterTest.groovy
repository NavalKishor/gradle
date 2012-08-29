/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.consumer.protocoladapter

import org.gradle.tooling.internal.idea.DefaultIdeaModuleDependency
import org.gradle.tooling.internal.idea.DefaultIdeaSingleEntryLibraryDependency
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.idea.IdeaDependency
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import org.gradle.util.Matchers
import spock.lang.Specification
import org.gradle.tooling.internal.consumer.*

/**
 * by Szczepan Faber, created at: 4/2/12
 */
class ProtocolToModelAdapterTest extends Specification {
    final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter()

    def createsProxyAdapterForProtocolModel() {
        TestProtocolModel protocolModel = Mock()

        expect:
        adapter.adapt(TestModel.class, protocolModel) instanceof TestModel
    }

    def proxiesAreEqualWhenTargetProtocolObjectsAreEqual() {
        TestProtocolModel protocolModel1 = Mock()
        TestProtocolModel protocolModel2 = Mock()

        def model = adapter.adapt(TestModel.class, protocolModel1)
        def equal = adapter.adapt(TestModel.class, protocolModel1)
        def different = adapter.adapt(TestModel.class, protocolModel2)

        expect:
        Matchers.strictlyEquals(model, equal)
        model != different
    }

    def methodInvocationOnModelDelegatesToTheProtocolModelObject() {
        TestProtocolModel protocolModel = Mock()
        _ * protocolModel.getName() >> 'name'

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.name == 'name'
    }

    def createsProxyAdapterForMethodReturnValue() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getProject() >> protocolProject
        _ * protocolProject.getName() >> 'name'

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.project instanceof TestProject
        model.project.name == 'name'
    }

    def doesNotAdaptNullReturnValue() {
        TestProtocolModel protocolModel = Mock()
        _ * protocolModel.getProject() >> null

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.project == null
    }

    def adaptsIterableToDomainObjectSet() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getChildren() >> [protocolProject]
        _ * protocolProject.getName() >> 'name'

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.children.size() == 1
        model.children[0] instanceof TestProject
        model.children[0].name == 'name'
    }

    def cachesPropertyValues() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject protocolProject = Mock()
        _ * protocolModel.getProject() >> protocolProject
        _ * protocolModel.getChildren() >> [protocolProject]
        _ * protocolProject.getName() >> 'name'

        expect:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.project.is(model.project)
        model.children.is(model.children)
    }

    def reportsMethodWhichDoesNotExistOnProtocolObject() {
        PartialTestProtocolModel protocolModel = Mock()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.project

        then:
        UnsupportedMethodException e = thrown()
        e.message.contains "TestModel.getProject()"
    }

    def propagatesExceptionThrownByProtocolObject() {
        TestProtocolModel protocolModel = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)
        model.name

        then:
        protocolModel.name >> { throw failure }
        RuntimeException e = thrown()
        e == failure
    }

    def isPropertySupportedMethodReturnsTrueWhenProtocolObjectHasAssociatedProperty() {
        TestProtocolModel protocolModel = Mock()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)

        then:
        model.projectSupported
    }

    def isPropertySupportedMethodReturnsFalseWhenProtocolObjectDoesNotHaveAssociatedProperty() {
        PartialTestProtocolModel protocolModel = Mock()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)

        then:
        !model.projectSupported
    }

    def overloadedGetterDelegatesToProtocolObject() {
        TestProtocolModel protocolModel = Mock()
        TestProtocolProject project = Mock()
        TestProject defaultProject = Mock()

        given:
        protocolModel.project >> project

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)

        then:
        model.getProject(defaultProject) != defaultProject
    }

    def overloadedGetterDelegatesReturnsDefaultValueWhenProtocolObjectDoesNotHaveAssociatedProperty() {
        PartialTestProtocolModel protocolModel = Mock()
        TestProject project = Mock()

        when:
        def model = adapter.adapt(TestModel.class, protocolModel)

        then:
        model.getProject(project) == project
    }

    def propertyHandlerCanOverrideGetterMethod() {
        ModelPropertyHandler propertyHandler = Mock()
        TestProtocolModel protocolModel = Mock()
        TestProject project = Mock()

        given:
        propertyHandler.shouldHandle({it.name == 'getProject'}, protocolModel) >> true
        propertyHandler.getPropertyValue({it.name == 'getProject'}, protocolModel) >> project

        when:
        def model = adapter.adapt(TestModel.class, protocolModel, propertyHandler)

        then:
        model.project == project

        and:
        0 * protocolModel._
    }

    def propertyHandlerCanProvideGetterMethodImplementation() {
        ModelPropertyHandler propertyHandler = Mock()
        PartialTestProtocolModel protocolModel = Mock()
        TestProject project = Mock()

        given:
        propertyHandler.shouldHandle({it.name == 'getProject'}, protocolModel) >> true
        propertyHandler.getPropertyValue({it.name == 'getProject'}, protocolModel) >> project

        when:
        def model = adapter.adapt(TestModel.class, protocolModel, propertyHandler)

        then:
        model.project == project

        and:
        0 * protocolModel._
    }

    def "adapts idea dependencies"() {
        def libraryDep = new GroovyClassLoader().loadClass(DefaultIdeaSingleEntryLibraryDependency.class.getCanonicalName()).newInstance()
        def moduleDep = new GroovyClassLoader().loadClass(DefaultIdeaModuleDependency.class.getCanonicalName()).newInstance()

        when:
        def library = adapter.adapt(IdeaDependency.class, libraryDep)
        def module  = adapter.adapt(IdeaDependency.class, moduleDep)

        then:
        library instanceof IdeaSingleEntryLibraryDependency
        module instanceof IdeaModuleDependency
    }
}
