package com.googlesource.gerrit.plugins.deleteproject;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationTest {
  private static final String CUSTOM_PARENT = "customParent";
  private static final String PLUGIN_NAME = "delete-project";

  @Mock private PluginConfigFactory pluginConfigFactoryMock;

  private Configuration deleteConfig;

  @Test
  public void defaultValuesAreLoaded() {
    when(pluginConfigFactoryMock.getFromGerritConfig(PLUGIN_NAME))
        .thenReturn(new PluginConfig(PLUGIN_NAME, new Config()));
    deleteConfig = new Configuration(pluginConfigFactoryMock, PLUGIN_NAME);

    assertThat(deleteConfig.getParentForDeletedProjects()).isEqualTo("Deleted-Projects");
    assertThat(deleteConfig.isDeletionWithTagsAllowed()).isTrue();
    assertThat(deleteConfig.shouldHideProjectOnPreserve()).isFalse();
  }

  @Test
  public void customValuesAreLoaded() {
    PluginConfig pluginConfig = new PluginConfig(PLUGIN_NAME, new Config());
    pluginConfig.setString("parentForDeletedProjects", CUSTOM_PARENT);
    pluginConfig.setBoolean("allowDeletionOfReposWithTags", false);
    pluginConfig.setBoolean("hideProjectOnPreserve", true);
    when(pluginConfigFactoryMock.getFromGerritConfig(PLUGIN_NAME)).thenReturn(pluginConfig);
    deleteConfig = new Configuration(pluginConfigFactoryMock, PLUGIN_NAME);

    assertThat(deleteConfig.getParentForDeletedProjects()).isEqualTo(CUSTOM_PARENT);
    assertThat(deleteConfig.isDeletionWithTagsAllowed()).isFalse();
    assertThat(deleteConfig.shouldHideProjectOnPreserve()).isTrue();
  }
}
