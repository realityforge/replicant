require 'buildr/git_auto_version'
require 'buildr/gpg'
require 'buildr/single_intermediate_layout'

GUICE_DEPS = [:google_guice, :google_guice_assistedinject, :aopalliance, :javax_inject]
DAGGER_GWT_DEPS = [:javax_inject, :javax_inject_sources, :dagger_core, :dagger_core_sources, :dagger_gwt]
DAGGER_COMPILER_DEPS = [:javax_inject, :dagger_core, :dagger_producers, :dagger_compiler, :googlejavaformat, :errorprone_javac, :javapoet, :guava]

POWERMOCK = [
  :objenesis, :powermock_core, :powermock_reflect, :powermock_testng_common, :powermock_testng,
  :powermock_testng_agent, :powermock_api_mockito, :powermock_api_mockito_common, :powermock_api_support, :javassist,
  :powermock_module_javaagent
]

AREZ_DEPS = [
 :arez_annotations, :arez_core, :arez_processor, :arez_component, :arez_extras, :arez_browser_extras, :braincheck, :anodoc, :jetbrains_annotations
]

GWT_DEPS = [:gwt_user] + DAGGER_GWT_DEPS
PROVIDED_DEPS = [:javax_jsr305, :javax_javaee, :glassfish_embedded]
KEYCLOAK_DEPS = [:simple_keycloak_service, :keycloak_adapter_core, :keycloak_adapter_spi, :keycloak_core, :keycloak_common]
COMPILE_DEPS = KEYCLOAK_DEPS
TEST_INFRA_DEPS = [:mockito, :guiceyloops, :glassfish_embedded, :testng]
OPTIONAL_DEPS = GWT_DEPS, TEST_INFRA_DEPS
TEST_DEPS = TEST_INFRA_DEPS + [:jndikit] + POWERMOCK

desc 'Replicant: Client-side state representation infrastructure'
define 'replicant' do
  project.group = 'org.realityforge.replicant'
  compile.options.source = '1.8'
  compile.options.target = '1.8'
  compile.options.lint = 'all'

  project.version = ENV['PRODUCT_VERSION'] if ENV['PRODUCT_VERSION']

  pom.add_apache_v2_license
  pom.add_github_project('realityforge/replicant')
  pom.add_developer('realityforge', 'Peter Donald')

  define 'shared' do
    compile.with :javax_jsr305

    package(:jar)
    package(:sources)
    package(:javadoc)

    gwt_enhance(project)
  end

  define 'shared-ee' do
    pom.provided_dependencies.concat PROVIDED_DEPS

    compile.with PROVIDED_DEPS

    test.using :testng
    test.compile.with TEST_DEPS

    package(:jar)
    package(:sources)
    package(:javadoc)
  end

  define 'server' do
    pom.provided_dependencies.concat PROVIDED_DEPS
    pom.optional_dependencies.concat OPTIONAL_DEPS

    compile.with PROVIDED_DEPS, COMPILE_DEPS, project('shared').package(:jar), project('shared-ee').package(:jar)

    package(:jar)
    package(:sources)
    package(:javadoc)

    test.using :testng
    test.compile.with TEST_DEPS
  end

  define 'client-common' do
    compile.with :javax_jsr305, :gwt_webpoller, :javax_inject, project('shared').package(:jar), AREZ_DEPS

    package(:jar)
    package(:sources)
    package(:javadoc)

    test.using :testng
    test.compile.with TEST_DEPS

    gwt_enhance(project)
  end

  define 'client-ee' do
    pom.provided_dependencies.concat PROVIDED_DEPS

    compile.with PROVIDED_DEPS,
                 project('shared-ee').package(:jar),
                 project('client-common').package(:jar),
                 project('client-common').compile.dependencies

    package(:jar)
    package(:sources)
    package(:javadoc)

    test.using :testng
    test.compile.with TEST_DEPS
  end

  define 'client-gwt' do
    compile.with GWT_DEPS,
                 project('client-common').package(:jar),
                 project('client-common').compile.dependencies

    package(:jar)
    package(:sources)
    package(:javadoc)

    gwt_enhance(project)

    processor_deps = Buildr.artifacts(DAGGER_COMPILER_DEPS)
    project.compile.enhance(processor_deps)
    project.compile.options[:other] = ['-processorpath', processor_deps.collect {|d| d.to_s}.join(File::PATH_SEPARATOR)]

    test.using :testng
    test.compile.with TEST_DEPS

    # Not sure what in this project breakes jacoco
    jacoco.enabled = false
  end

  define 'client-qa-support' do
    compile.with GUICE_DEPS,
                 TEST_INFRA_DEPS,
                 project('client-common').package(:jar),
                 project('client-common').compile.dependencies

    package(:jar)
    package(:sources)
    package(:javadoc)

    test.using :testng
    test.compile.with TEST_DEPS
  end

  ipr.add_component_from_artifact(:idea_codestyle)

  iml.add_jruby_facet
end
