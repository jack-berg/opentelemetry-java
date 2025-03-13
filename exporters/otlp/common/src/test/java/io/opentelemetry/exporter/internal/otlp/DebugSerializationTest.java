/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.otlp;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.exporter.internal.marshal.Marshaler;
import io.opentelemetry.exporter.internal.otlp.traces.LowAllocationTraceRequestMarshaler;
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DebugSerializationTest {

  private byte[] binary;
  private int binarySize;
  private String json;

  @ParameterizedTest
  @MethodSource("args")
  void test(Function<List<SpanData>, Marshaler> f) throws IOException {
    String json =
        "{\"resource\":{\"attributes\":[{\"key\":\"container.id\",\"value\":{\"stringValue\":\"ad80d452f2424a1d27edc42354d2c4f8b2889f92bcd64458b17aa36623abef6e\"}},{\"key\":\"deployment.environment\",\"value\":{\"stringValue\":\"staging\"}},{\"key\":\"git.commit.sha\",\"value\":{\"stringValue\":\"ed78275dd19501b8d6b07df4f71789f36245bd37\"}},{\"key\":\"git.repository_url\",\"value\":{\"stringValue\":\"https://github.com/\"}},{\"key\":\"host.arch\",\"value\":{\"stringValue\":\"amd64\"}},{\"key\":\"host.name\",\"value\":{\"stringValue\":\"sample-rollout-b96597558-6wqfg\"}},{\"key\":\"os.description\",\"value\":{\"stringValue\":\"Linux 5.10.233-224.894.amzn2.x86_64\"}},{\"key\":\"os.type\",\"value\":{\"stringValue\":\"linux\"}},{\"key\":\"process.runtime.description\",\"value\":{\"stringValue\":\"Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.6+7-LTS\"}},{\"key\":\"process.runtime.name\",\"value\":{\"stringValue\":\"OpenJDK Runtime Environment\"}},{\"key\":\"process.runtime.version\",\"value\":{\"stringValue\":\"21.0.6+7-LTS\"}},{\"key\":\"service.instance.id\",\"value\":{\"stringValue\":\"a5c52ed2-2463-47cf-bb80-aa18d740831d\"}},{\"key\":\"service.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"service.version\",\"value\":{\"stringValue\":\"v2025-03-07-ed78275-SNAPSHOT\"}},{\"key\":\"telemetry.distro.name\",\"value\":{\"stringValue\":\"opentelemetry-spring-boot-starter\"}},{\"key\":\"telemetry.distro.version\",\"value\":{\"stringValue\":\"2.13.3\"}},{\"key\":\"telemetry.sdk.language\",\"value\":{\"stringValue\":\"java\"}},{\"key\":\"telemetry.sdk.name\",\"value\":{\"stringValue\":\"opentelemetry\"}},{\"key\":\"telemetry.sdk.version\",\"value\":{\"stringValue\":\"1.47.0\"}}]},\"scopeSpans\":[{\"scope\":{\"name\":\"io.opentelemetry.r2dbc-1.0\",\"version\":\"2.13.3-alpha\",\"attributes\":[]},\"spans\":[{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"c52fcbd336086c65\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"299c5d36dba90889\",\"name\":\"SELECT sample\",\"kind\":3,\"startTimeUnixNano\":\"1741365472947418246\",\"endTimeUnixNano\":\"1741365472957761585\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\" SELECT sample_list.* FROM sample_list INNER JOIN sample_category ON sample_list.sample_category_id = sample_category.id WHERE sample_category.name = $1 AND sample_category.deleted_at IS NULL AND sample_list.effective_at <= $2 AND sample_list.active = true AND sample_list.deleted_at IS NULL ORDER BY sample_list.effective_at DESC, sample_list.created_at DESC LIMIT ? \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"44674e8bc5415492\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"70eb7c672c4d4af9\",\"name\":\"SELECT sample\",\"kind\":3,\"startTimeUnixNano\":\"1741365472948585033\",\"endTimeUnixNano\":\"1741365472958946604\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\" SELECT sample_list.* FROM sample_list INNER JOIN sample_category ON sample_list.sample_category_id = sample_category.id WHERE sample_category.name = $1 AND sample_category.deleted_at IS NULL AND sample_list.effective_at <= $2 AND sample_list.active = true AND sample_list.deleted_at IS NULL ORDER BY sample_list.effective_at DESC, sample_list.created_at DESC LIMIT ? \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"d82c2b47a1d63f0f\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"013c0bc4db6e86b6\",\"name\":\"SELECT sample\",\"kind\":3,\"startTimeUnixNano\":\"1741365472927393132\",\"endTimeUnixNano\":\"1741365472961876027\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\" SELECT sample_list.* FROM sample_list INNER JOIN sample_category ON sample_list.sample_category_id = sample_category.id WHERE sample_category.name = $1 AND sample_category.deleted_at IS NULL AND sample_list.effective_at <= $2 AND sample_list.active = true AND sample_list.deleted_at IS NULL ORDER BY sample_list.effective_at DESC, sample_list.created_at DESC LIMIT ? \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"ce73499edb4222cf\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"93cc9fbdbf6c44ba\",\"name\":\"SELECT sample\",\"kind\":3,\"startTimeUnixNano\":\"1741365472966594131\",\"endTimeUnixNano\":\"1741365472974954467\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\" SELECT sample_list.* FROM sample_list INNER JOIN sample_category ON sample_list.sample_category_id = sample_category.id WHERE sample_category.name = $1 AND sample_category.deleted_at IS NULL AND sample_list.effective_at <= $2 AND sample_list.active = true AND sample_list.deleted_at IS NULL ORDER BY sample_list.effective_at DESC, sample_list.created_at DESC LIMIT ? \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"3dde92f7252f9083\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"2bb53729c32a58a8\",\"name\":\"SELECT sample\",\"kind\":3,\"startTimeUnixNano\":\"1741365473007231493\",\"endTimeUnixNano\":\"1741365473039184823\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\" SELECT sample_list.* FROM sample_list INNER JOIN sample_category ON sample_list.sample_category_id = sample_category.id WHERE sample_category.name = $1 AND sample_category.deleted_at IS NULL AND sample_list.effective_at <= $2 AND sample_list.active = true AND sample_list.deleted_at IS NULL ORDER BY sample_list.effective_at DESC, sample_list.created_at DESC LIMIT ? \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"a0059a5619dde6c1\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"3f6010f926e7619b\",\"name\":\"SELECT sample\",\"kind\":3,\"startTimeUnixNano\":\"1741365473194132149\",\"endTimeUnixNano\":\"1741365473197247191\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\" SELECT sample_list.* FROM sample_list INNER JOIN sample_category ON sample_list.sample_category_id = sample_category.id WHERE sample_category.name = $1 AND sample_category.deleted_at IS NULL AND sample_list.effective_at <= $2 AND sample_list.active = true AND sample_list.deleted_at IS NULL ORDER BY sample_list.effective_at DESC, sample_list.created_at DESC LIMIT ? \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"1bc4041332aab995\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"3f6010f926e7619b\",\"name\":\"SELECT sample.sample_contains_value\",\"kind\":3,\"startTimeUnixNano\":\"1741365473230147420\",\"endTimeUnixNano\":\"1741365473235680679\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.sql.table\",\"value\":{\"stringValue\":\"sample_contains_value\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\"SELECT * FROM sample_contains_value WHERE sample_category_name = $1 AND sample_name = $2 AND input_key_name = $3 AND LOWER(value) = LOWER($4) AND effective_at <= $5 AND (deleted_at IS NULL OR deleted_at > $5) \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"240e79f7811eab1d\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"3f6010f926e7619b\",\"name\":\"SELECT sample.sample_contains_value\",\"kind\":3,\"startTimeUnixNano\":\"1741365473233147968\",\"endTimeUnixNano\":\"1741365473237846638\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.sql.table\",\"value\":{\"stringValue\":\"sample_contains_value\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\"SELECT * FROM sample_contains_value WHERE sample_category_name = $1 AND sample_name = $2 AND input_key_name = $3 AND LOWER(value) = LOWER($4) AND effective_at <= $5 AND (deleted_at IS NULL OR deleted_at > $5) \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"cfe6fdab47cb2a3c\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"3f6010f926e7619b\",\"name\":\"SELECT sample.sample_contains_value\",\"kind\":3,\"startTimeUnixNano\":\"1741365473242221457\",\"endTimeUnixNano\":\"1741365473243975748\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.sql.table\",\"value\":{\"stringValue\":\"sample_contains_value\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\"SELECT * FROM sample_contains_value WHERE sample_category_name = $1 AND sample_name = $2 AND input_key_name = $3 AND LOWER(value) = LOWER($4) AND effective_at <= $5 AND (deleted_at IS NULL OR deleted_at > $5) \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"413b54aaa34f31c8\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"3f6010f926e7619b\",\"name\":\"SELECT sample.sample_contains_value\",\"kind\":3,\"startTimeUnixNano\":\"1741365473240876696\",\"endTimeUnixNano\":\"1741365473244717424\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.sql.table\",\"value\":{\"stringValue\":\"sample_contains_value\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\"SELECT * FROM sample_contains_value WHERE sample_category_name = $1 AND sample_name = $2 AND input_key_name = $3 AND LOWER(value) = LOWER($4) AND effective_at <= $5 AND (deleted_at IS NULL OR deleted_at > $5) \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"b6e8180446b2e7ed\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"3f6010f926e7619b\",\"name\":\"SELECT sample.sample_contains_value\",\"kind\":3,\"startTimeUnixNano\":\"1741365473227036327\",\"endTimeUnixNano\":\"1741365473248095744\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.sql.table\",\"value\":{\"stringValue\":\"sample_contains_value\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\"SELECT * FROM sample_contains_value WHERE sample_category_name = $1 AND sample_name = $2 AND input_key_name = $3 AND LOWER(value) = LOWER($4) AND effective_at <= $5 AND (deleted_at IS NULL OR deleted_at > $5) \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257},{\"traceId\":\"67cb20e100000000563eb49c34f57ff1\",\"spanId\":\"8235f0a748cc48dc\",\"traceState\":\"dd=s:1;p:2dacc7c9ea2ad466;t.dm:-1;t.tid:67cb20e100000000\",\"parentSpanId\":\"b85c7f52252d0cc7\",\"name\":\"SELECT sample\",\"kind\":3,\"startTimeUnixNano\":\"1741365473465152194\",\"endTimeUnixNano\":\"1741365473470305873\",\"attributes\":[{\"key\":\"db.operation\",\"value\":{\"stringValue\":\"SELECT\"}},{\"key\":\"server.port\",\"value\":{\"intValue\":\"5432\"}},{\"key\":\"db.name\",\"value\":{\"stringValue\":\"sample\"}},{\"key\":\"db.user\",\"value\":{\"stringValue\":\"master\"}},{\"key\":\"db.connection_string\",\"value\":{\"stringValue\":\"postgresql://postgres-rds-staging.rds.amazonaws.com:5432\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"postgres-rds-staging.rds.amazonaws.com\"}},{\"key\":\"db.system\",\"value\":{\"stringValue\":\"postgresql\"}},{\"key\":\"db.statement\",\"value\":{\"stringValue\":\" SELECT sample_list.* FROM sample_list INNER JOIN sample_category ON sample_list.sample_category_id = sample_category.id WHERE sample_category.name = $1 AND sample_category.deleted_at IS NULL AND sample_list.effective_at <= $2 AND sample_list.active = true AND sample_list.deleted_at IS NULL ORDER BY sample_list.effective_at DESC, sample_list.created_at DESC LIMIT ? \"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":257}]},{\"scope\":{\"name\":\"io.opentelemetry.spring-webflux-5.3\",\"version\":\"2.13.3-alpha\",\"attributes\":[]},\"spans\":[{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"70eb7c672c4d4af9\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"815c7942a438407d\",\"name\":\"POST /sample/v1/sample-categories/{category-name}/evaluate\",\"kind\":2,\"startTimeUnixNano\":\"1741365472822401504\",\"endTimeUnixNano\":\"1741365473004169914\",\"attributes\":[{\"key\":\"server.port\",\"value\":{\"intValue\":\"8080\"}},{\"key\":\"network.peer.port\",\"value\":{\"intValue\":\"56575\"}},{\"key\":\"url.scheme\",\"value\":{\"stringValue\":\"http\"}},{\"key\":\"categoryName\",\"value\":{\"stringValue\":\"recommendations\"}},{\"key\":\"http.response.status_code\",\"value\":{\"intValue\":\"200\"}},{\"key\":\"url.path\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/recommendations/evaluate\"}},{\"key\":\"network.peer.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"internal-k8s-lb-staging.elb.amazonaws.com\"}},{\"key\":\"client.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"user_agent.original\",\"value\":{\"stringValue\":\"ReactorNetty/1.1.12\"}},{\"key\":\"http.route\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/{category-name}/evaluate\"}},{\"key\":\"http.request.method\",\"value\":{\"stringValue\":\"POST\"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":769},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"299c5d36dba90889\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"0b40adf45794ee8d\",\"name\":\"POST /sample/v1/sample-categories/{category-name}/evaluate\",\"kind\":2,\"startTimeUnixNano\":\"1741365472822422621\",\"endTimeUnixNano\":\"1741365473005874829\",\"attributes\":[{\"key\":\"server.port\",\"value\":{\"intValue\":\"8080\"}},{\"key\":\"network.peer.port\",\"value\":{\"intValue\":\"41932\"}},{\"key\":\"url.scheme\",\"value\":{\"stringValue\":\"http\"}},{\"key\":\"categoryName\",\"value\":{\"stringValue\":\"healthandwellness\"}},{\"key\":\"http.response.status_code\",\"value\":{\"intValue\":\"200\"}},{\"key\":\"url.path\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/healthandwellness/evaluate\"}},{\"key\":\"network.peer.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"internal-k8s-lb-staging.elb.amazonaws.com\"}},{\"key\":\"client.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"user_agent.original\",\"value\":{\"stringValue\":\"ReactorNetty/1.1.12\"}},{\"key\":\"http.route\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/{category-name}/evaluate\"}},{\"key\":\"http.request.method\",\"value\":{\"stringValue\":\"POST\"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":769},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"93cc9fbdbf6c44ba\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"db6f361024cd7afa\",\"name\":\"POST /sample/v1/sample-categories/{category-name}/evaluate\",\"kind\":2,\"startTimeUnixNano\":\"1741365472824396951\",\"endTimeUnixNano\":\"1741365473025500108\",\"attributes\":[{\"key\":\"server.port\",\"value\":{\"intValue\":\"8080\"}},{\"key\":\"network.peer.port\",\"value\":{\"intValue\":\"7078\"}},{\"key\":\"url.scheme\",\"value\":{\"stringValue\":\"http\"}},{\"key\":\"categoryName\",\"value\":{\"stringValue\":\"claims\"}},{\"key\":\"http.response.status_code\",\"value\":{\"intValue\":\"200\"}},{\"key\":\"url.path\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/claims/evaluate\"}},{\"key\":\"network.peer.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"internal-k8s-lb-staging.elb.amazonaws.com\"}},{\"key\":\"client.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"user_agent.original\",\"value\":{\"stringValue\":\"ReactorNetty/1.1.12\"}},{\"key\":\"http.route\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/{category-name}/evaluate\"}},{\"key\":\"http.request.method\",\"value\":{\"stringValue\":\"POST\"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":769},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"013c0bc4db6e86b6\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"dd5be42975d5f835\",\"name\":\"POST /sample/v1/sample-categories/{category-name}/evaluate\",\"kind\":2,\"startTimeUnixNano\":\"1741365472822193322\",\"endTimeUnixNano\":\"1741365473052594246\",\"attributes\":[{\"key\":\"server.port\",\"value\":{\"intValue\":\"8080\"}},{\"key\":\"network.peer.port\",\"value\":{\"intValue\":\"40342\"}},{\"key\":\"url.scheme\",\"value\":{\"stringValue\":\"http\"}},{\"key\":\"categoryName\",\"value\":{\"stringValue\":\"uhcdigitalv2\"}},{\"key\":\"http.response.status_code\",\"value\":{\"intValue\":\"200\"}},{\"key\":\"url.path\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/uhcdigitalv2/evaluate\"}},{\"key\":\"network.peer.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"internal-k8s-lb-staging.elb.amazonaws.com\"}},{\"key\":\"client.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"user_agent.original\",\"value\":{\"stringValue\":\"ReactorNetty/1.1.12\"}},{\"key\":\"http.route\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/{category-name}/evaluate\"}},{\"key\":\"http.request.method\",\"value\":{\"stringValue\":\"POST\"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":769},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"2bb53729c32a58a8\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"f2406296738013e3\",\"name\":\"GET /sample/v1/sample-categories/{category-name}/active-sample-list\",\"kind\":2,\"startTimeUnixNano\":\"1741365472970788623\",\"endTimeUnixNano\":\"1741365473055521085\",\"attributes\":[{\"key\":\"server.port\",\"value\":{\"intValue\":\"8080\"}},{\"key\":\"network.peer.port\",\"value\":{\"intValue\":\"37706\"}},{\"key\":\"url.scheme\",\"value\":{\"stringValue\":\"http\"}},{\"key\":\"categoryName\",\"value\":{\"stringValue\":\"rewards\"}},{\"key\":\"http.response.status_code\",\"value\":{\"intValue\":\"200\"}},{\"key\":\"url.path\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/rewards/active-sample-list\"}},{\"key\":\"network.peer.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"internal-k8s-lb-staging.elb.amazonaws.com\"}},{\"key\":\"client.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"user_agent.original\",\"value\":{\"stringValue\":\"ReactorNetty/1.1.12\"}},{\"key\":\"http.route\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/{category-name}/active-sample-list\"}},{\"key\":\"http.request.method\",\"value\":{\"stringValue\":\"GET\"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":769},{\"traceId\":\"67cb20e000000000282a2a4e326fc5c2\",\"spanId\":\"3f6010f926e7619b\",\"traceState\":\"dd=s:1;p:267a38e36a2059a3;t.dm:-1;t.tid:67cb20e000000000\",\"parentSpanId\":\"8050928ff9a32835\",\"name\":\"POST /sample/v1/sample-categories/{category-name}/evaluate\",\"kind\":2,\"startTimeUnixNano\":\"1741365473166816230\",\"endTimeUnixNano\":\"1741365473248374104\",\"attributes\":[{\"key\":\"server.port\",\"value\":{\"intValue\":\"8080\"}},{\"key\":\"network.peer.port\",\"value\":{\"intValue\":\"26671\"}},{\"key\":\"url.scheme\",\"value\":{\"stringValue\":\"http\"}},{\"key\":\"categoryName\",\"value\":{\"stringValue\":\"dental\"}},{\"key\":\"http.response.status_code\",\"value\":{\"intValue\":\"200\"}},{\"key\":\"url.path\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/dental/evaluate\"}},{\"key\":\"network.peer.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"internal-k8s-lb-staging.elb.amazonaws.com\"}},{\"key\":\"client.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"user_agent.original\",\"value\":{\"stringValue\":\"ReactorNetty/1.1.12\"}},{\"key\":\"http.route\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/{category-name}/evaluate\"}},{\"key\":\"http.request.method\",\"value\":{\"stringValue\":\"POST\"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":769},{\"traceId\":\"67cb20e100000000563eb49c34f57ff1\",\"spanId\":\"0180a323ec97f0a6\",\"traceState\":\"dd=s:1;p:2dacc7c9ea2ad466;t.dm:-1;t.tid:67cb20e100000000\",\"parentSpanId\":\"1208321c5e7ea927\",\"name\":\"GET /sample/v1/sample-categories/{category-name}/active-sample-list\",\"kind\":2,\"startTimeUnixNano\":\"1741365473339847982\",\"endTimeUnixNano\":\"1741365473349569289\",\"attributes\":[{\"key\":\"server.port\",\"value\":{\"intValue\":\"8080\"}},{\"key\":\"network.peer.port\",\"value\":{\"intValue\":\"40342\"}},{\"key\":\"url.scheme\",\"value\":{\"stringValue\":\"http\"}},{\"key\":\"categoryName\",\"value\":{\"stringValue\":\"rewards\"}},{\"key\":\"http.response.status_code\",\"value\":{\"intValue\":\"200\"}},{\"key\":\"url.path\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/rewards/active-sample-list\"}},{\"key\":\"network.peer.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"internal-k8s-lb-staging.elb.amazonaws.com\"}},{\"key\":\"client.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"user_agent.original\",\"value\":{\"stringValue\":\"ReactorNetty/1.1.12\"}},{\"key\":\"http.route\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/{category-name}/active-sample-list\"}},{\"key\":\"http.request.method\",\"value\":{\"stringValue\":\"GET\"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":769},{\"traceId\":\"67cb20e100000000563eb49c34f57ff1\",\"spanId\":\"cbbecd508a4aa91b\",\"traceState\":\"dd=s:1;p:2dacc7c9ea2ad466;t.dm:-1;t.tid:67cb20e100000000\",\"parentSpanId\":\"f137b13b1b3d7708\",\"name\":\"POST /sample/v1/sample-categories/{category-name}/evaluate\",\"kind\":2,\"startTimeUnixNano\":\"1741365473439980397\",\"endTimeUnixNano\":\"1741365473455375738\",\"attributes\":[{\"key\":\"server.port\",\"value\":{\"intValue\":\"8080\"}},{\"key\":\"network.peer.port\",\"value\":{\"intValue\":\"56575\"}},{\"key\":\"url.scheme\",\"value\":{\"stringValue\":\"http\"}},{\"key\":\"categoryName\",\"value\":{\"stringValue\":\"rewards\"}},{\"key\":\"http.response.status_code\",\"value\":{\"intValue\":\"200\"}},{\"key\":\"url.path\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/rewards/evaluate\"}},{\"key\":\"network.peer.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"internal-k8s-lb-staging.elb.amazonaws.com\"}},{\"key\":\"client.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"user_agent.original\",\"value\":{\"stringValue\":\"ReactorNetty/1.1.12\"}},{\"key\":\"http.route\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/{category-name}/evaluate\"}},{\"key\":\"http.request.method\",\"value\":{\"stringValue\":\"POST\"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":769},{\"traceId\":\"67cb20e100000000563eb49c34f57ff1\",\"spanId\":\"b85c7f52252d0cc7\",\"traceState\":\"dd=s:1;p:2dacc7c9ea2ad466;t.dm:-1;t.tid:67cb20e100000000\",\"parentSpanId\":\"f4302aab6e9d9a53\",\"name\":\"POST /sample/v1/sample-categories/{category-name}/evaluate\",\"kind\":2,\"startTimeUnixNano\":\"1741365473443209034\",\"endTimeUnixNano\":\"1741365473493427944\",\"attributes\":[{\"key\":\"server.port\",\"value\":{\"intValue\":\"8080\"}},{\"key\":\"network.peer.port\",\"value\":{\"intValue\":\"7078\"}},{\"key\":\"url.scheme\",\"value\":{\"stringValue\":\"http\"}},{\"key\":\"categoryName\",\"value\":{\"stringValue\":\"card\"}},{\"key\":\"http.response.status_code\",\"value\":{\"intValue\":\"200\"}},{\"key\":\"url.path\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/card/evaluate\"}},{\"key\":\"network.peer.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"server.address\",\"value\":{\"stringValue\":\"internal-k8s-lb-staging.elb.amazonaws.com\"}},{\"key\":\"client.address\",\"value\":{\"stringValue\":\"10.68.12.143\"}},{\"key\":\"user_agent.original\",\"value\":{\"stringValue\":\"ReactorNetty/1.1.12\"}},{\"key\":\"http.route\",\"value\":{\"stringValue\":\"/sample/v1/sample-categories/{category-name}/evaluate\"}},{\"key\":\"http.request.method\",\"value\":{\"stringValue\":\"POST\"}}],\"events\":[],\"links\":[],\"status\":{},\"flags\":769}]}],\"schemaUrl\":\"https://opentelemetry.io/schemas/1.24.0\"}";
    ResourceSpans.Builder builder = ResourceSpans.newBuilder();
    JsonFormat.parser().merge(json, builder);
    ResourceSpans resourceSpans = builder.build();
    ExportTraceServiceRequest exportTraceServiceRequest = ExportTraceServiceRequest.newBuilder()
        .addResourceSpans(resourceSpans).build();

    // System.out.println(resourceSpans);

    List<SpanData> spanData = toSpanData(resourceSpans);

    // System.out.println(spanData);

    Marshaler binaryMarshaler = f.apply(spanData);
    ByteArrayOutputStream binaryBaos = new ByteArrayOutputStream();
    int binarySize = binaryMarshaler.getBinarySerializedSize();
    binaryMarshaler.writeBinaryTo(binaryBaos);
    assertThat(binaryBaos.toByteArray().length).isEqualTo(binarySize);

    if (binary == null) {
      binary = binaryBaos.toByteArray();
      this.binarySize = binarySize;
    } else {
      assertThat(binarySize).isEqualTo(this.binarySize);
      assertThat(binary).isEqualTo(binaryBaos.toByteArray());
    }

    Marshaler jsonMarshaler = f.apply(spanData);
    ByteArrayOutputStream jsonBaos = new ByteArrayOutputStream();
    jsonMarshaler.writeJsonTo(jsonBaos);

    if (this.json == null) {
      this.json = new String(jsonBaos.toByteArray(), StandardCharsets.UTF_8);
    } else {
      assertThat(new String(jsonBaos.toByteArray(), StandardCharsets.UTF_8)).isEqualTo(this.json);
    }
  }

  private static Stream<Arguments> args() {
    return Stream.of(
        Arguments.of(
            f(
                spanData -> {
                  LowAllocationTraceRequestMarshaler lowAllocationTraceRequestMarshaler =
                      new LowAllocationTraceRequestMarshaler();
                  lowAllocationTraceRequestMarshaler.initialize(spanData);
                  return lowAllocationTraceRequestMarshaler;
                })),
        Arguments.of(f(TraceRequestMarshaler::create)));
  }

  private static Function<List<SpanData>, Marshaler> f(Function<List<SpanData>, Marshaler> f) {
    return f;
  }

  private static List<SpanData> toSpanData(ResourceSpans resourceSpans) {
    List<SpanData> spanData = new ArrayList<>();

    io.opentelemetry.sdk.resources.Resource resource = toResource(resourceSpans);
    for (ScopeSpans scopeSpans : resourceSpans.getScopeSpansList()) {
      InstrumentationScopeInfo scope = toScope(scopeSpans);
      for (Span span : scopeSpans.getSpansList()) {
        spanData.add(toSpan(resource, scope, span));
      }
    }

    return spanData;
  }

  private static SpanData toSpan(Resource resource, InstrumentationScopeInfo scope, Span span) {
    SpanContext spanContext =
        SpanContext.create(
            TraceId.fromBytes(span.getTraceId().toByteArray()),
            SpanId.fromBytes(span.getSpanId().toByteArray()),
            TraceFlags.fromByte((byte) span.getFlags()),
            TraceState.getDefault() // TODO
            );
    SpanContext parentSpanContext =
        SpanContext.create(
            TraceId.fromBytes(span.getTraceId().toByteArray()),
            SpanId.fromBytes(span.getParentSpanId().toByteArray()),
            TraceFlags.getDefault(),
            TraceState.getDefault());

    Attributes attributes = toAttributes(span.getAttributesList());

    return TestSpanData.builder()
        .setResource(resource)
        .setInstrumentationScopeInfo(scope)
        .setSpanContext(spanContext)
        .setParentSpanContext(parentSpanContext)
        .setName(span.getName())
        .setKind(toSpanKind(span.getKind()))
        .setStartEpochNanos(span.getStartTimeUnixNano())
        .setEndEpochNanos(span.getEndTimeUnixNano())
        .setAttributes(toAttributes(span.getAttributesList()))
        .setEvents(Collections.emptyList()) // TODO
        .setLinks(Collections.emptyList()) // TODO
        .setStatus(toStatusData(span.getStatus()))
        .setTotalAttributeCount(attributes.size())
        .setHasEnded(true)
        .build();
  }

  private static StatusData toStatusData(Status status) {
    StatusCode code = StatusCode.UNSET;
    if (Status.StatusCode.STATUS_CODE_ERROR.equals(status.getCode())) {
      code = StatusCode.ERROR;
    }
    if (Status.StatusCode.STATUS_CODE_OK.equals(status.getCode())) {
      code = StatusCode.OK;
    }

    return StatusData.create(code, status.getMessage());
  }

  private static SpanKind toSpanKind(Span.SpanKind spanKind) {
    switch (spanKind) {
      case SPAN_KIND_INTERNAL:
        return SpanKind.INTERNAL;
      case SPAN_KIND_SERVER:
        return SpanKind.SERVER;
      case SPAN_KIND_CLIENT:
        return SpanKind.CLIENT;
      case SPAN_KIND_PRODUCER:
        return SpanKind.PRODUCER;
      case SPAN_KIND_CONSUMER:
        return SpanKind.CONSUMER;
    }
    throw new IllegalArgumentException();
  }

  private static InstrumentationScopeInfo toScope(ScopeSpans scopeSpans) {
    return InstrumentationScopeInfo.builder(scopeSpans.getScope().getName())
        .setVersion(scopeSpans.getScope().getVersion())
        .setSchemaUrl(scopeSpans.getSchemaUrl())
        .build();
  }

  private static io.opentelemetry.sdk.resources.Resource toResource(ResourceSpans resourceSpans) {
    ResourceBuilder builder = io.opentelemetry.sdk.resources.Resource.empty().toBuilder();
    builder.putAll(toAttributes(resourceSpans.getResource().getAttributesList()));
    builder.setSchemaUrl(resourceSpans.getSchemaUrl());
    return builder.build();
  }

  private static Attributes toAttributes(List<KeyValue> keyValueList) {
    AttributesBuilder builder = Attributes.builder();
    keyValueList.forEach(
        keyValue -> {
          AnyValue value = keyValue.getValue();
          if (value.hasStringValue()) {
            builder.put(keyValue.getKey(), value.getStringValue());
            return;
          }
        });
    return builder.build();
  }
}
