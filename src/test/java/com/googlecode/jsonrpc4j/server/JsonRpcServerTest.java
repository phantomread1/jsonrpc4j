package com.googlecode.jsonrpc4j.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.jsonrpc4j.ErrorResolver;
import com.googlecode.jsonrpc4j.JsonRpcServer;
import com.googlecode.jsonrpc4j.util.Util;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.easymock.MockType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.googlecode.jsonrpc4j.JsonRpcBasicServer.*;
import static com.googlecode.jsonrpc4j.util.Util.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(EasyMockRunner.class)
public class JsonRpcServerTest {

	@Mock(type = MockType.NICE)
	private ServiceInterface mockService;
	private JsonRpcServer jsonRpcServer;

	@Before
	public void setup() {
		jsonRpcServer = new JsonRpcServer(Util.mapper, mockService, ServiceInterface.class);
	}

	@Test
	public void testGetMethod_badRequest_corruptParams() throws Exception {
		EasyMock.expect(mockService.testMethod("Whirinaki")).andReturn("Forest");
		EasyMock.replay(mockService);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test-get");
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.addParameter("id", Integer.toString(123));
		request.addParameter("method", "testMethod");
		request.addParameter("params", "{BROKEN}");

		jsonRpcServer.handle(request, response);

		assertTrue(MockHttpServletResponse.SC_BAD_REQUEST == response.getStatus());

		JsonNode errorNode = error(toByteArrayOutputStream(response.getContentAsByteArray()));

		assertNotNull(errorNode);
		assertEquals(errorCode(errorNode).asLong(), (long) ErrorResolver.JsonError.PARSE_ERROR.code);
	}

	@Test
	public void testGetMethod_badRequest_noMethod() throws Exception {
		EasyMock.expect(mockService.testMethod("Whirinaki")).andReturn("Forest");
		EasyMock.replay(mockService);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test-get");
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.addParameter("id", Integer.toString(123));
		// no method!
		request.addParameter("params", net.iharder.Base64.encodeBytes("[\"Whirinaki\"]".getBytes(StandardCharsets.UTF_8)));

		jsonRpcServer.handle(request, response);

		assertTrue(MockHttpServletResponse.SC_NOT_FOUND == response.getStatus());

		JsonNode errorNode = error(toByteArrayOutputStream(response.getContentAsByteArray()));

		assertNotNull(errorNode);
		assertEquals(errorCode(errorNode).asLong(), (long) ErrorResolver.JsonError.METHOD_NOT_FOUND.code);
	}

	private void checkSuccessfulResponse(MockHttpServletResponse response) throws IOException {
		assertTrue(HttpServletResponse.SC_OK == response.getStatus());

		JsonNode responseEnvelope = decodeAnswer(toByteArrayOutputStream(response.getContentAsByteArray()));
		assertTrue(responseEnvelope.get(ID).isIntegralNumber());
		assertEquals(responseEnvelope.get(ID).asLong(), 123L);
		assertTrue(responseEnvelope.get(RESULT).isTextual());
		assertEquals(responseEnvelope.get(RESULT).asText(), "For?est");
	}

	@Test
	public void test_contentType() throws Exception {
		EasyMock.expect(mockService.testMethod("Whir?inaki")).andReturn("For?est");
		EasyMock.replay(mockService);

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test-post");
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.setContentType("application/json");
		request.setContent("{\"jsonrpc\":\"2.0\",\"id\":123,\"method\":\"testMethod\",\"params\":[\"Whir?inaki\"]}".getBytes(StandardCharsets.UTF_8));

		jsonRpcServer.setContentType("flip/flop");

		jsonRpcServer.handle(request, response);

		assertTrue("flip/flop".equals(response.getContentType()));
		checkSuccessfulResponse(response);
	}

	@Test
	public void testGetMethod_base64Params() throws Exception {
		EasyMock.expect(mockService.testMethod("Whir?inaki")).andReturn("For?est");
		EasyMock.replay(mockService);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test-get");
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.addParameter("id", Integer.toString(123));
		request.addParameter("method", "testMethod");
		request.addParameter("params", net.iharder.Base64.encodeBytes("[\"Whir?inaki\"]".getBytes(StandardCharsets.UTF_8)));

		jsonRpcServer.handle(request, response);

		assertTrue("application/json-rpc".equals(response.getContentType()));
		checkSuccessfulResponse(response);
	}

	@Test
	public void testGetMethod_unencodedParams() throws Exception {
		EasyMock.expect(mockService.testMethod("Whir?inaki")).andReturn("For?est");
		EasyMock.replay(mockService);

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test-get");
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.addParameter("id", Integer.toString(123));
		request.addParameter("method", "testMethod");
		request.addParameter("params", "[\"Whir?inaki\"]");

		jsonRpcServer.handle(request, response);

		assertTrue("application/json-rpc".equals(response.getContentType()));
		checkSuccessfulResponse(response);
	}

	@Test
	public void testNullRequest() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test-get");
		MockHttpServletResponse response = new MockHttpServletResponse();

		jsonRpcServer.handle(request, response);
		assertTrue(MockHttpServletResponse.SC_BAD_REQUEST == response.getStatus());
	}

	@Test
	public void testGzipResponse() throws IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test-post");
		request.addHeader(ACCEPT_ENCODING, "gzip");
		request.setContentType("application/json");
		request.setContent("{\"jsonrpc\":\"2.0\",\"id\":123,\"method\":\"testMethod\",\"params\":[\"Whir?inaki\"]}".getBytes(StandardCharsets.UTF_8));

		MockHttpServletResponse response = new MockHttpServletResponse();

		jsonRpcServer = new JsonRpcServer(Util.mapper, mockService, ServiceInterface.class, true);
		jsonRpcServer.handle(request, response);

		byte[] compressed = response.getContentAsByteArray();
		String sb = getCompressedResponseContent(compressed);

		Assert.assertEquals(sb, "{\"jsonrpc\":\"2.0\",\"id\":123,\"result\":null}");
		Assert.assertEquals("gzip", response.getHeader(CONTENT_ENCODING));
	}

	@Test
	public void testGzipRequest() throws IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test-post");
		request.addHeader(CONTENT_ENCODING, "gzip");
		request.setContentType("application/json");
		byte[] bytes = "{\"jsonrpc\":\"2.0\",\"id\":123,\"method\":\"testMethod\",\"params\":[\"Whir?inaki\"]}".getBytes(StandardCharsets.UTF_8);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GZIPOutputStream gos = new GZIPOutputStream(baos);
		gos.write(bytes);
		gos.close();

		request.setContent(baos.toByteArray());

		MockHttpServletResponse response = new MockHttpServletResponse();
		jsonRpcServer = new JsonRpcServer(Util.mapper, mockService, ServiceInterface.class, true);
		jsonRpcServer.handle(request, response);

		String responseContent = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
		Assert.assertEquals(responseContent, "{\"jsonrpc\":\"2.0\",\"id\":123,\"result\":null}\n");
		Assert.assertNull(response.getHeader(CONTENT_ENCODING));
	}

	@Test
	public void testGzipRequestAndResponse() throws IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test-post");
		request.addHeader(CONTENT_ENCODING, "gzip");
		request.addHeader(ACCEPT_ENCODING, "gzip");
		request.setContentType("application/json");
		byte[] bytes = "{\"jsonrpc\":\"2.0\",\"id\":123,\"method\":\"testMethod\",\"params\":[\"Whir?inaki\"]}".getBytes(StandardCharsets.UTF_8);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GZIPOutputStream gos = new GZIPOutputStream(baos);
		gos.write(bytes);
		gos.close();

		request.setContent(baos.toByteArray());

		MockHttpServletResponse response = new MockHttpServletResponse();
		jsonRpcServer = new JsonRpcServer(Util.mapper, mockService, ServiceInterface.class, true);
		jsonRpcServer.handle(request, response);

		byte[] compressed = response.getContentAsByteArray();
		String sb = getCompressedResponseContent(compressed);

		Assert.assertEquals(sb, "{\"jsonrpc\":\"2.0\",\"id\":123,\"result\":null}");
		Assert.assertEquals("gzip", response.getHeader(CONTENT_ENCODING));
	}

	private String getCompressedResponseContent(byte[] compressed) throws IOException {
		GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressed));
		InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8);
		BufferedReader bufferedReader = new BufferedReader(inputStreamReader, 2048);
		StringBuilder sb = new StringBuilder();
		String readed;
		while ((readed = bufferedReader.readLine()) != null) {
			sb.append(readed);
		}
		return sb.toString();
	}

	// Service and service interfaces used in test

	public interface ServiceInterface {
		String testMethod(String param1);
	}

}
