# OpenTelemetry File Based SDK Configuration Proposal

This is a proposal to add a file based configuration scheme for configuring OpenTelemetry SDKs.

## Goal

The goal is to define a f

## Why

The [environment configuration specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/sdk-environment-variables.md) is useful, but its flat structure is limiting. Some examples:

* [Views](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/sdk.md#view) have a fairly large number of configurable arguments. The community has resisted adding a flat scheme for configuring views in anticipation of a more natural file based option.
* Multiple span exporters, log record exporters, and metric exporters can be configured, but one set of configuration arguments dictates the configuration of their respective span processors, log record processors, and periodic metric readers.
* Similarly, there is no way to have multiple instances of the same exporter configured to export to different locations. I.e. you can't configure two OTLP exporters to export to two different vendors.
* The environment configuration specification doesn't work well with configuring extension components not packaged with the SDK. How can a user use environment variables to indicate that a custom exporter should be used which filters some data before delgating to the otlp exporter?

A file based configuration scheme that supports complex types like objects and arrays would allow many environment variable configuration problems to be solved.

A file based configuration scheme would make it easy to share SDK configuration, with many benefits:

* Teams could use standard configuration files checked into source code.
* Vendors could share configuration snippets with users.
* SDKs could print out configuration for debugging purposes to help maintainers triage issues.
* SDKs could be remotely configured by sending file configurations using [OpAMP](https://github.com/open-telemetry/opamp-spec) (Issue [#2207](https://github.com/open-telemetry/opentelemetry-specification/issues/2207)).

## Proposal

### YAML Serialization

This proposal calls for using [YAML](https://yaml.org/) as the serialization format for configuration files.

YAML is a human-readable data serialization language. It's a strong choice for a file based configuration schema for the following reasons:

* YAML is very popular. It has implementations in many languages and is used for configuring the [opentelemetry-collector](https://github.com/open-telemetry/opentelemetry-collector).
* YAML supports [anchors](https://yaml.org/spec/1.2.2/#3222-anchors-and-aliases), which allows for reusing bits of configuration. JSON does not support this concept.
* YAML is less verbose than other popular serialization languages like JSON and XML. 

TODO: add yaml version?

### JSON Schema

It will be important that the file based configuration schema is defined in a single place, and is expressed in a manner that minimizes different interpretations across languages.

This proposal calls for using [JSON Schema](https://json-schema.org/) to describe the schema for the following reasons:

* JSON Schema is very popular. It's used by the [OpenAPI Specification](https://swagger.io/specification/), by [kubernetes](https://github.com/kubernetes/kubernetes/tree/master/api/openapi-spec), and many other projects.
* Despite its name, JSON Schema can be (and often is) used with YAML.
* By using JSON Schema, SDKs can quickly validate configuration files for structural correctness, and we can promote a uniform definition of validity across SDK implementations.

TODO: add json schema version

### Schema Design

The [sdk-schema] directory contains the proposed schema definition. For an example that demonstrates all proposed options see [kitchen-sink](./src/test/resources/sdk/kitchen-sink.yaml). Here's a sample of a relatively simple configuration:

```yaml
# Anchor for the OTLP exporter configuration, which is reused in span, metric, and log exporter configuration
otlp_args: &otlpArgs
  protocol: grpc
  endpoint: https://my-remote-otlp-host.com:4317
  headers:
    api-key: 1234
  compression: gzip

sdk:
  # Configure the resource.
  resource:
    # List of enabled resource detectors. Each detector has a name (FQCN for java), and an optional list of excluded keys. Detector name supports wildcard syntax. Detectors are invoked and merged sequentially.
    detectors:
      - name: "*"
    # List of static resource attribute key / value pairs, which are merged last with the resources provided by detectors.
    attributes:
      service.name: my-service
      service.instance.id: 1234
  # General attribute limits, applicable to span and log record attributes.
  attribute_limits:
    attribute_count_limit: 10
    attribute_value_length_limit: 100
  # Tracer provider configuration.
  tracer_provider:
    # List of span processors, to be added sequentially. Each span processor has a name and args used to configure it.
    span_processors:
      # Add batch span processor configured to export with the OTLP exporter.
      - name: batch
        args:
          # Batch span processor takes exporter as an arg, which is composed of a name an args used to configure it.
          exporter:
            name: otlp
            # Reference the otlp_args anchor defined earlier in the file.
            args: *otlpArgs
    # The sampler. Each sampler has a name and args used to configure it.
    sampler:
      name: parentbased
      args:
        # The parentbased sampler takes root_sampler as an arg, which is another sampler itself.
        root_sampler:
          name: traceidratio
          args:
            ratio: 0.01
  # Meter provider configuration.
  meter_provider:
    # List of metric readers. Each metric reader has a name and args used to configure it.
    metric_readers:
      # Add periodic metric reader configured to export with the logging exporter using the default interval settings.
      - name: periodic
        args:
          # Periodic metric reader takes exporter as an arg, which is composed of a name an args used to configure it.
          exporter:
            name: otlp
            # Reference the otlp_args anchor defined earlier in the file.
            args: *otlpArgs
    # List of views. Each view consists of a selector defining criteria for which instruments are selected, and a view defining the resulting metric.
    views:
      # Add a view which configures the drop aggregation for instruments whose name matches "http.server.*.size".
      - selector:
          instrument_name: "http.server.*.size"
        view:
          aggregation:
            name: drop
  # Logger provider configuration.
  logger_provider:
    # List of log record processors, to be added sequentially. Each log record processor has a name and args used to configure it.
    log_record_processors:
      # Add batch log record processor configured to export with the OTLP exporter.
      - name: batch
        args:
          # Batch log processor takes exporter as an arg, which is composed of a name an args used to configure it.
          exporter:
            name: otlp
            args: *otlpArgs
  # List of context propagators. Each propagator has a name and args used to configure it.
  propagators:
    - name: tracecontext

```

#### Schema which mirrors programmatic SDK configuration

The schema should fallout from the programatic configuration options of the SDK. The [environment configuration specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/sdk-environment-variables.md) should be able to translate to deterministically map to an equivalent file configuration, but it shouldn't rigidly mirror it when compromises were made. 

For example, the environment configuration contains [Exporter Selection](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/sdk-environment-variables.md#exporter-selection) options, as well as [Batch Span Processor](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/sdk-environment-variables.md#batch-span-processor) options. You can have multiple exporters, but can't specify different options for each exporter's associated batch span processor. The file configuration schema reflects programmatic configuration of processors and exporters: Tracer provider is configured with a list of processors. Each has a name and an args object, which varies based on the name of the processor. Batch span processor expects an exporter argument, which consists of a name, and an args object, which varies based on the name of the exporter. Batch span processor also accepts optional arguments for queue size, delay, etc. 

The following configuration demonstrates configuring two processors. The first is a simple processor configured with the logging exporter. The second is a batch processor configured to export to an OTLP server. This isn't possible with the environment configuration specification, or with a file configuration specification that attempts to mirror the environment configuration specification:

```yaml
sdk:
  tracer_provider:
    span_processors:
      - name: simple
        args:
          exporter:
            name: logging
      - name: batch
        args:
          exporter:
            name: otlp
            args:
              endpoint: https://otlp:4317
              compression: gzip
```

#### Common patterns for similar concepts

There are certain ideas that recur across the SDK configuration landscape. For example, there are several places where configuration calls for referencing components by name, and providing some configuration arguments. Samplers are configurable by name, and several expect configuration arguments (e.g. parentbased sampler expects the parent sampler to be defined). Exporters are configurable by name, and expect configuration argument which vary by exporter. Span processors, log record processors, metric readers, propagators, and more follow the same type of pattern. 

The proposed schema uses the same pattern in all these cases:

```yaml
# The name of the component
name: foo
# Set of arguments use to configure the component
args:
  key: value
```

The schema of args will vary based on the named component. For example, the jaeger exporter would have [user and password](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/sdk-environment-variables.md#jaeger-exporter) arguments (amongst others) not shared by the otlp exporter. 

Nesting the arguments under `args` guarantees that argument names won't conflict (e.g. a component can have an argument called `name`). It also allows additional keys to be added to the top level in the future without worrying about naming conflicts.

The schema of args can be arbitraily complex based on the requirements of the named component. For example, the [ParentBased](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/sdk.md#parentbased) sampler has options for whether or not to sample based on whether the parent is local or remote, and delgates to a root sampler if there is no parent. Configuring it via YAML might look like:

```yaml
name: parentbased
args:
  root_sampler:
    name: traceidratiobased
    args:
      ratio: 0.1
  remote_parent_sampled_sampler:
    name: alwayson
  remote_parent_not_sampled_sampler:
    name: alwaysoff
  local_parent_sampled_sampler:
    name: alwayson
  local_parent_not_sampled_sampler:
    name: alwaysoff
```

This recreates the default configuration of the parentbased sampler, configuring the root to be a traceidratiobased sampler with a ratio of 0.1. Notice how the `root_sampler` argument is a complex object that matches the sampler schema - args are not limited to primitives. File based configuration will enable scenarios that are not currently possible without programmatic configuration. Note, in this example `remote_parent_sampled_sampler`,  `remote_parent_not_sampled_sampler`, `local_parent_sampled_sampler`, and `local_parent_not_sampled_sampler` are explicitly configured despite aligning with the defaults for demonstration purposes.

#### Composable pieces

Maintaining a single file describing the schema of a complex domain can quickly become unwieldy. JSON Schema has a [system](https://json-schema.org/understanding-json-schema/structuring.html#) of ids and refs that allow schemas to be broken down into small parts and composed together. In additional to improving maintainability, it allows for reusing recurring bits. For example, the concept of an exporter occurs across the log, trace, and metric signals. The schema definitions of metric reader, span processor, and log record processor can each use the same exporter schema definition.

The proposed schema is composed of several small pieces. For example, the root [sdk schema](./sdk-schema/sdk.json) references a separately defined [resource schema](./sdk-schema/resource.json) by defining resource as `"resource": { "$ref": "/schemas/sdkconfig/resource" }`.


https://github.com/open-telemetry/opentelemetry-specification/issues/2181
https://github.com/open-telemetry/opentelemetry-specification/issues/2207
https://github.com/open-telemetry/opentelemetry-specification/issues/1773