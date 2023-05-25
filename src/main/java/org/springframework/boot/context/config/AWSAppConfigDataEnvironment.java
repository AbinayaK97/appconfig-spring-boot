package org.springframework.boot.context.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.DefaultPropertiesPropertySource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.log.LogMessage;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AWSAppConfigDataEnvironment {

    static final String ON_NOT_FOUND_PROPERTY = "spring.config.on-not-found";

    static final ConfigDataLocation[] DEFAULT_SEARCH_LOCATIONS;
    private static final Bindable<List<String>> STRING_LIST = Bindable.listOf(String.class);
    private static final ConfigDataEnvironmentContributors.BinderOption[] ALLOW_INACTIVE_BINDING = {};
    private static final ConfigDataEnvironmentContributors.BinderOption[] DENY_INACTIVE_BINDING = {ConfigDataEnvironmentContributors.BinderOption.FAIL_ON_BIND_TO_INACTIVE_SOURCE};

    static {
        List<ConfigDataLocation> locations = new ArrayList<>();
        locations.add(ConfigDataLocation.of("aws-app-config"));
        DEFAULT_SEARCH_LOCATIONS = locations.toArray(new ConfigDataLocation[0]);
    }

    private final DeferredLogFactory logFactory;

    private final Log logger;

    private final ConfigDataNotFoundAction notFoundAction;

    private final ConfigurableBootstrapContext bootstrapContext;

    private final ConfigurableEnvironment environment;

    private final ConfigDataLocationResolvers resolvers;

    private final Collection<String> additionalProfiles;

    private final ConfigDataEnvironmentUpdateListener environmentUpdateListener;

    private final ConfigDataLoaders loaders;

    private final ConfigDataEnvironmentContributors contributors;

    AWSAppConfigDataEnvironment(DeferredLogFactory logFactory, ConfigurableBootstrapContext bootstrapContext,
                                ConfigurableEnvironment environment, ResourceLoader resourceLoader, Collection<String> additionalProfiles,
                                ConfigDataEnvironmentUpdateListener environmentUpdateListener) {
        Binder binder = Binder.get(environment);
        UseLegacyConfigProcessingException.throwIfRequested(binder);
        this.logFactory = logFactory;
        this.logger = logFactory.getLog(getClass());
        this.notFoundAction = binder.bind(ON_NOT_FOUND_PROPERTY, ConfigDataNotFoundAction.class)
                .orElse(ConfigDataNotFoundAction.FAIL);
        this.bootstrapContext = bootstrapContext;
        this.environment = environment;
        this.resolvers = createConfigDataLocationResolvers(logFactory, bootstrapContext, binder, resourceLoader);
        this.additionalProfiles = additionalProfiles;
        this.environmentUpdateListener = (environmentUpdateListener != null) ? environmentUpdateListener
                : ConfigDataEnvironmentUpdateListener.NONE;
        this.loaders = new ConfigDataLoaders(logFactory, bootstrapContext, resourceLoader.getClassLoader());
        this.contributors = createContributors();
    }

    protected ConfigDataLocationResolvers createConfigDataLocationResolvers(DeferredLogFactory logFactory,
                                                                            ConfigurableBootstrapContext bootstrapContext, Binder binder, ResourceLoader resourceLoader) {
        return new ConfigDataLocationResolvers(logFactory, bootstrapContext, binder, resourceLoader);
    }

    private ConfigDataEnvironmentContributors createContributors() {
        this.logger.trace("Building config data environment contributors");
        MutablePropertySources propertySources = this.environment.getPropertySources();
        List<ConfigDataEnvironmentContributor> contributors = new ArrayList<>(propertySources.size() + 10);
        PropertySource<?> defaultPropertySource = null;
        for (PropertySource<?> propertySource : propertySources) {
            if (DefaultPropertiesPropertySource.hasMatchingName(propertySource)) {
                defaultPropertySource = propertySource;
            } else {
                this.logger.trace(LogMessage.format("Creating wrapped config data contributor for '%s'",
                        propertySource.getName()));
                contributors.add(ConfigDataEnvironmentContributor.ofExisting(propertySource));
            }
        }
        contributors.addAll(getInitialImportContributors());
        if (defaultPropertySource != null) {
            this.logger.trace("Creating wrapped config data contributor for default property source");
            contributors.add(ConfigDataEnvironmentContributor.ofExisting(defaultPropertySource));
        }
        return createContributors(contributors);
    }

    protected ConfigDataEnvironmentContributors createContributors(
            List<ConfigDataEnvironmentContributor> contributors) {
        return new ConfigDataEnvironmentContributors(this.logFactory, this.bootstrapContext, contributors);
    }

    private List<ConfigDataEnvironmentContributor> getInitialImportContributors() {
        List<ConfigDataEnvironmentContributor> initialContributors = new ArrayList<>();
        addInitialImportContributors(initialContributors, DEFAULT_SEARCH_LOCATIONS);
        return initialContributors;
    }

    private void addInitialImportContributors(List<ConfigDataEnvironmentContributor> initialContributors,
                                              ConfigDataLocation[] locations) {
        for (int i = locations.length - 1; i >= 0; i--) {
            initialContributors.add(createInitialImportContributor(locations[i]));
        }
    }

    private ConfigDataEnvironmentContributor createInitialImportContributor(ConfigDataLocation location) {
        return ConfigDataEnvironmentContributor.ofInitialImport(location);
    }

  void processAndApply() {
    ConfigDataImporter importer = new ConfigDataImporter(this.logFactory, this.notFoundAction, this.resolvers, this.loaders);
    registerBootstrapBinder(this.contributors, null, DENY_INACTIVE_BINDING);
    ConfigDataEnvironmentContributors contributors = processInitial(this.contributors, importer);
    ConfigDataActivationContext activationContext = createActivationContext(contributors.getBinder(null, ConfigDataEnvironmentContributors.BinderOption.FAIL_ON_BIND_TO_INACTIVE_SOURCE));
    contributors = processWithoutProfiles(contributors, importer, activationContext);
    activationContext = withProfiles(contributors, activationContext);
    contributors = processWithProfiles(contributors, importer, activationContext);
    this.logger.info(LogMessage.format("Set application properties for profile '%s' from AWS AppConfig", activationContext.getProfiles().getActive().get(0)));
    applyToEnvironment(contributors, activationContext, importer.getLoadedLocations(), importer.getOptionalLocations());
  }

    private ConfigDataEnvironmentContributors processInitial(ConfigDataEnvironmentContributors contributors,
                                                             ConfigDataImporter importer) {
        this.logger.info("Processing initial config data environment contributors without activation context");
        contributors = contributors.withProcessedImports(importer, null);
        registerBootstrapBinder(contributors, null, DENY_INACTIVE_BINDING);
        return contributors;
    }

    private ConfigDataActivationContext createActivationContext(Binder initialBinder) {
        this.logger.info("Creating config data activation context from initial contributions");
        try {
            return new ConfigDataActivationContext(this.environment, initialBinder);
        } catch (BindException ex) {
            if (ex.getCause() instanceof InactiveConfigDataAccessException) {
                throw (InactiveConfigDataAccessException) ex.getCause();
            }
            throw ex;
        }
    }

    private ConfigDataEnvironmentContributors processWithoutProfiles(ConfigDataEnvironmentContributors contributors,
                                                                     ConfigDataImporter importer, ConfigDataActivationContext activationContext) {
        this.logger.info("Processing config data environment contributors with initial activation context");
        contributors = contributors.withProcessedImports(importer, activationContext);
        registerBootstrapBinder(contributors, activationContext, DENY_INACTIVE_BINDING);
        return contributors;
    }

    private ConfigDataActivationContext withProfiles(ConfigDataEnvironmentContributors contributors,
                                                     ConfigDataActivationContext activationContext) {
        this.logger.info("Deducing profiles from current config data environment contributors");
        Binder binder = contributors.getBinder(activationContext,
                (contributor) -> !contributor.hasConfigDataOption(ConfigData.Option.IGNORE_PROFILES),
                ConfigDataEnvironmentContributors.BinderOption.FAIL_ON_BIND_TO_INACTIVE_SOURCE);
        try {
            Set<String> additionalProfiles = new LinkedHashSet<>(this.additionalProfiles);
            additionalProfiles.addAll(getIncludedProfiles(contributors, activationContext));
            Profiles profiles = new Profiles(this.environment, binder, additionalProfiles);
            return activationContext.withProfiles(profiles);
        } catch (BindException ex) {
            if (ex.getCause() instanceof InactiveConfigDataAccessException) {
                throw (InactiveConfigDataAccessException) ex.getCause();
            }
            throw ex;
        }
    }

    private Collection<? extends String> getIncludedProfiles(ConfigDataEnvironmentContributors contributors,
                                                             ConfigDataActivationContext activationContext) {
        PlaceholdersResolver placeholdersResolver = new ConfigDataEnvironmentContributorPlaceholdersResolver(
                contributors, activationContext, null, true);
        Set<String> result = new LinkedHashSet<>();
        for (ConfigDataEnvironmentContributor contributor : contributors) {
            ConfigurationPropertySource source = contributor.getConfigurationPropertySource();
            if (source != null && !contributor.hasConfigDataOption(ConfigData.Option.IGNORE_PROFILES)) {
                Binder binder = new Binder(Collections.singleton(source), placeholdersResolver);
                binder.bind(Profiles.INCLUDE_PROFILES, STRING_LIST).ifBound((includes) -> {
                    if (!contributor.isActive(activationContext)) {
                        InactiveConfigDataAccessException.throwIfPropertyFound(contributor, Profiles.INCLUDE_PROFILES);
                        InactiveConfigDataAccessException.throwIfPropertyFound(contributor,
                                Profiles.INCLUDE_PROFILES.append("[0]"));
                    }
                    result.addAll(includes);
                });
            }
        }
        return result;
    }

    private ConfigDataEnvironmentContributors processWithProfiles(ConfigDataEnvironmentContributors contributors,
                                                                  ConfigDataImporter importer, ConfigDataActivationContext activationContext) {
        this.logger.info("Processing config data environment contributors with profile activation context");
        contributors = contributors.withProcessedImports(importer, activationContext);
        registerBootstrapBinder(contributors, activationContext, ALLOW_INACTIVE_BINDING);
        return contributors;
    }

    private void registerBootstrapBinder(ConfigDataEnvironmentContributors contributors,
                                         ConfigDataActivationContext activationContext, ConfigDataEnvironmentContributors.BinderOption... binderOptions) {
        this.bootstrapContext.register(Binder.class, BootstrapRegistry.InstanceSupplier
                .from(() -> contributors.getBinder(activationContext, binderOptions)).withScope(BootstrapRegistry.Scope.PROTOTYPE));
    }

    private void applyToEnvironment(ConfigDataEnvironmentContributors contributors,
                                    ConfigDataActivationContext activationContext, Set<ConfigDataLocation> loadedLocations,
                                    Set<ConfigDataLocation> optionalLocations) {
        checkForInvalidProperties(contributors);
        checkMandatoryLocations(contributors, activationContext, loadedLocations, optionalLocations);
        MutablePropertySources propertySources = this.environment.getPropertySources();
        applyContributor(contributors, activationContext, propertySources);
        DefaultPropertiesPropertySource.moveToEnd(propertySources);
        Profiles profiles = activationContext.getProfiles();
        this.logger.info(LogMessage.format("Setting default profiles: %s", profiles.getDefault()));
        this.environment.setDefaultProfiles(StringUtils.toStringArray(profiles.getDefault()));
        this.logger.info(LogMessage.format("Setting active profiles: %s", profiles.getActive()));
        this.environment.setActiveProfiles(StringUtils.toStringArray(profiles.getActive()));
        this.environmentUpdateListener.onSetProfiles(profiles);
    }

    private void applyContributor(ConfigDataEnvironmentContributors contributors,
                                  ConfigDataActivationContext activationContext, MutablePropertySources propertySources) {
        this.logger.info("Applying config data environment contributions");
        for (ConfigDataEnvironmentContributor contributor : contributors) {
            PropertySource<?> propertySource = contributor.getPropertySource();
            if (contributor.getKind() == ConfigDataEnvironmentContributor.Kind.BOUND_IMPORT && propertySource != null) {
                if (!contributor.isActive(activationContext)) {
                    this.logger.trace(
                            LogMessage.format("Skipping inactive property source '%s'", propertySource.getName()));
                } else {
                    this.logger
                            .trace(LogMessage.format("Adding imported property source '%s'", propertySource.getName()));
                    propertySources.addLast(propertySource);
                    this.environmentUpdateListener.onPropertySourceAdded(propertySource, contributor.getLocation(),
                            contributor.getResource());
                }
            }
        }
    }

    private void checkForInvalidProperties(ConfigDataEnvironmentContributors contributors) {
        for (ConfigDataEnvironmentContributor contributor : contributors) {
            InvalidConfigDataPropertyException.throwOrWarn(this.logger, contributor);
        }
    }

    private void checkMandatoryLocations(ConfigDataEnvironmentContributors contributors,
                                         ConfigDataActivationContext activationContext, Set<ConfigDataLocation> loadedLocations,
                                         Set<ConfigDataLocation> optionalLocations) {
        Set<ConfigDataLocation> mandatoryLocations = new LinkedHashSet<>();
        for (ConfigDataEnvironmentContributor contributor : contributors) {
            if (contributor.isActive(activationContext)) {
                mandatoryLocations.addAll(getMandatoryImports(contributor));
            }
        }
        for (ConfigDataEnvironmentContributor contributor : contributors) {
            if (contributor.getLocation() != null) {
                mandatoryLocations.remove(contributor.getLocation());
            }
        }
        mandatoryLocations.removeAll(loadedLocations);
        mandatoryLocations.removeAll(optionalLocations);
        if (!mandatoryLocations.isEmpty()) {
            for (ConfigDataLocation mandatoryLocation : mandatoryLocations) {
                this.notFoundAction.handle(this.logger, new ConfigDataLocationNotFoundException(mandatoryLocation));
            }
        }
    }

    private Set<ConfigDataLocation> getMandatoryImports(ConfigDataEnvironmentContributor contributor) {
        List<ConfigDataLocation> imports = contributor.getImports();
        Set<ConfigDataLocation> mandatoryLocations = new LinkedHashSet<>(imports.size());
        for (ConfigDataLocation location : imports) {
            if (!location.isOptional()) {
                mandatoryLocations.add(location);
            }
        }
        return mandatoryLocations;
    }
}
