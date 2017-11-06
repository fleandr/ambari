/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distribut
 * ed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ambari.server.topology;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.ambari.server.AmbariException;
import org.apache.ambari.server.ObjectNotFoundException;
import org.apache.ambari.server.controller.AmbariManagementController;
import org.apache.ambari.server.controller.StackV2;
import org.apache.ambari.server.controller.StackV2Factory;
import org.apache.ambari.server.controller.utilities.PropertyHelper;
import org.apache.ambari.server.orm.dao.BlueprintV2DAO;
import org.apache.ambari.server.orm.entities.BlueprintV2Entity;
import org.apache.ambari.server.orm.entities.StackEntity;
import org.apache.ambari.server.stack.NoSuchStackException;
import org.apache.ambari.server.state.StackId;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.Inject;

public class BlueprintV2Factory {
  // Blueprints
  protected static final String BLUEPRINT_NAME_PROPERTY_ID =
    PropertyHelper.getPropertyId("Blueprints", "blueprint_name");
  protected static final String STACK_NAME_PROPERTY_ID =
    PropertyHelper.getPropertyId("Blueprints", "stack_name");
  protected static final String STACK_VERSION_PROPERTY_ID =
    PropertyHelper.getPropertyId("Blueprints", "stack_version");

  // Host Groups
  protected static final String HOST_GROUP_PROPERTY_ID = "host_groups";
  protected static final String HOST_GROUP_NAME_PROPERTY_ID = "name";
  protected static final String HOST_GROUP_CARDINALITY_PROPERTY_ID = "cardinality";

  // Host Group Components
  protected static final String COMPONENT_PROPERTY_ID ="components";
  protected static final String COMPONENT_NAME_PROPERTY_ID ="name";
  protected static final String COMPONENT_PROVISION_ACTION_PROPERTY_ID = "provision_action";

  // Configurations
  protected static final String CONFIGURATION_PROPERTY_ID = "configurations";
  protected static final String PROPERTIES_PROPERTY_ID = "properties";
  protected static final String PROPERTIES_ATTRIBUTES_PROPERTY_ID = "properties_attributes";

  protected static final String SETTINGS_PROPERTY_ID = "settings";

  private boolean prettyPrintJson = false;
  private static BlueprintV2DAO blueprintDAO;
  private ConfigurationFactory configFactory = new ConfigurationFactory();

  private StackV2Factory stackFactory;

  protected BlueprintV2Factory() {

  }
  protected BlueprintV2Factory(StackV2Factory stackFactory) {
    this.stackFactory = stackFactory;
  }

  public static BlueprintV2Factory create(AmbariManagementController controller) {
    return new BlueprintV2Factory(new StackV2Factory(controller));
  }

  public static BlueprintV2Factory create(StackV2Factory factory) {
    return new BlueprintV2Factory(factory);
  }

  public BlueprintV2 getBlueprint(String blueprintName) throws NoSuchStackException, NoSuchBlueprintException, IOException {
    BlueprintV2Entity entity =
      Optional.ofNullable(blueprintDAO.findByName(blueprintName)).orElseThrow(() -> new NoSuchBlueprintException(blueprintName));
    return convertFromEntity(entity);
  }

  public BlueprintV2 convertFromJson(String json) throws IOException {
    BlueprintImplV2 blueprintV2 = createObjectMapper().readValue(json, BlueprintImplV2.class);
    blueprintV2.postDeserialization();
    blueprintV2.setStacks(
      blueprintV2.getStackIds().stream().collect(Collectors.toMap(
        stackId -> new StackId(stackId),
        stackId -> parseStack(new StackId(stackId))
      ))
    );
    return blueprintV2;
  }


  public BlueprintV2 convertFromEntity(BlueprintV2Entity blueprintEntity) throws IOException {
    return convertFromJson(blueprintEntity.getContent());
  }

  public Map<String, Object> convertToMap(BlueprintV2Entity entity) throws IOException {
    return createObjectMapper().readValue(entity.getContent(), HashMap.class);
  }

  private StackV2 parseStack(StackId stackId) {
    try {
      return stackFactory.create(stackId.getStackName(), stackId.getStackVersion());
    } catch (AmbariException e) {
      throw new IllegalArgumentException(
        String.format("Unable to parse stack. name=%s, version=%s", stackId.getStackName(), stackId.getStackVersion()),
        e);
    }
  }

  private StackV2 parseStack(StackEntity stackEntity) {
    return parseStack(new StackId(stackEntity.getStackName(), stackEntity.getStackVersion()));
  }

  public BlueprintV2Entity convertToEntity(BlueprintV2 blueprint) throws JsonProcessingException {
    BlueprintV2Entity entity = new BlueprintV2Entity();
    String content = convertToJson(blueprint);
    entity.setContent(content);
    entity.setBlueprintName(blueprint.getName());
    entity.setSecurityType(blueprint.getSecurity().getType());
    entity.setSecurityDescriptorReference(blueprint.getSecurity().getDescriptorReference());
    return entity;
  }

  public String convertToJson(BlueprintV2 blueprint) throws JsonProcessingException {
    return createObjectMapper().writeValueAsString(blueprint);

  }

  /**
   * Convert a map of properties to a blueprint entity.
   *
   * @param properties  property map
   * @param securityConfiguration security related properties
   * @return new blueprint entity
   */
  @SuppressWarnings("unchecked")
  public BlueprintV2 createBlueprint(Map<String, Object> properties, SecurityConfiguration securityConfiguration) throws NoSuchStackException, IOException {
    String name = String.valueOf(properties.get(BLUEPRINT_NAME_PROPERTY_ID));
    // String.valueOf() will return "null" if value is null
    if (name.equals("null") || name.isEmpty()) {
      //todo: should throw a checked exception from here
      throw new IllegalArgumentException("Blueprint name must be provided");
    }
    ObjectMapper om = createObjectMapper();
    String json = om.writeValueAsString(properties);
    BlueprintImplV2 blueprint = om.readValue(json, BlueprintImplV2.class);
    blueprint.postDeserialization();
    Map<String, StackV2> stacks = new HashMap<>();
    for (String stackId: blueprint.getStackIds()) {
      stacks.put(stackId, stackFactory.create(stackId));
    }
    blueprint.setSecurityConfiguration(securityConfiguration);
    return blueprint;
  }

  protected StackV2 createStack(Map<String, Object> properties) throws NoSuchStackException {
    String stackName = String.valueOf(properties.get(STACK_NAME_PROPERTY_ID));
    String stackVersion = String.valueOf(properties.get(STACK_VERSION_PROPERTY_ID));
    try {
      //todo: don't pass in controller
      return stackFactory.create(stackName, stackVersion);
    } catch (ObjectNotFoundException e) {
      throw new NoSuchStackException(stackName, stackVersion);
    } catch (AmbariException e) {
      //todo:
      throw new RuntimeException("An error occurred parsing the stack information.", e);
    }
  }

  public boolean isPrettyPrintJson() {
    return prettyPrintJson;
  }

  public void setPrettyPrintJson(boolean prettyPrintJson) {
    this.prettyPrintJson = prettyPrintJson;
  }

  ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule("CustomModel", Version.unknownVersion());
    SimpleAbstractTypeResolver resolver = new SimpleAbstractTypeResolver();
    resolver.addMapping(HostGroupV2.class, HostGroupV2Impl.class);
    module.setAbstractTypes(resolver);
    mapper.registerModule(module);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    if (prettyPrintJson) {
      mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    return mapper;
  }

  /**
   * Static initialization.
   *
   * @param dao  blueprint data access object
   */
  @Inject
  public static void init(BlueprintV2DAO dao) {
    blueprintDAO = dao;
  }
}
