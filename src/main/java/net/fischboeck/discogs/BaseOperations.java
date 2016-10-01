//
//  Copyright 2016 M. Fischboeck 
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

package net.fischboeck.discogs;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class BaseOperations {

	protected static final String DEFAULT_BASE_URL = "https://api.discogs.com";
	
	protected final Logger log = LoggerFactory.getLogger(getClass());
	
	protected CloseableHttpClient httpClient;
	protected ObjectMapper mapper;
	
	protected boolean isAuthenticatedClient;
	
	
	BaseOperations(CloseableHttpClient client, ObjectMapper mapper) {
		this.httpClient = client;
		this.mapper = mapper;
	}
	

	protected StringBuilder createPageParameters(StringBuilder urlBuilder, PageRequest page) {
		if (page != null) {
			urlBuilder.append("?page=");
			
			if (page.getPage() > PageRequest.DEFAULT_PAGE) {
				urlBuilder.append(page.getPage());
			} else {
				urlBuilder.append(PageRequest.DEFAULT_PAGE);
			}
			
			if (page.getSize() > 0) {
				urlBuilder.append(page.getSize());
			} else {
				urlBuilder.append(PageRequest.DEFAULT_PAGE_SIZE);
			}
		}
		return urlBuilder;
	}

	
	protected <T> T doGetRequest(String url, Class<T> type) throws ClientException {
		
		log.debug("[doRequest] Requesting URL {}", url);
		
		CloseableHttpResponse response = null;
		
		try {
			response = doHttpRequest(new HttpGet(url));
			HttpEntity entity = response.getEntity();
		
			BufferedInputStream in2 = new BufferedInputStream(entity.getContent());
			
			T retval = mapper.readValue(in2, type);
			return retval;
		} catch (EntityNotFoundException ex) {
			return null;
		} catch (JsonMappingException jme) {
			throw new ClientException(jme.getMessage());
		} catch (JsonParseException jpe) {
			throw new ClientException(jpe.getMessage());
		} catch (IOException ioe) {
			throw new ClientException(ioe.getMessage());
		} finally {
			closeSafe(response);
		}
	}
	
	
	protected <T> T doGetRequest(String url, JavaType type) throws ClientException {
	
		log.debug("[doGetRequest] Requesting URL {}", url);
		
		CloseableHttpResponse response = null;
		
		try {
			response = doHttpRequest(new HttpGet(url));
			HttpEntity entity = response.getEntity();
			
			BufferedInputStream in2 = new BufferedInputStream(entity.getContent());
			
			T retval = mapper.readValue(in2, type);
			return retval;
		} catch (EntityNotFoundException ex) {
			return null;
		} catch (JsonMappingException jme) {
			throw new ClientException(jme.getMessage());
		} catch (JsonParseException jpe) {
			throw new ClientException(jpe.getMessage());
		} catch (IOException ioe) {
			throw new ClientException(ioe.getMessage());
		} finally {
			closeSafe(response);
		}
	}
	
	
	protected <T> T doPostRequest(String url, Object body, Class<T> type) throws ClientException {
		
		log.debug("[doPostRequest] url={}", url);
		
		CloseableHttpResponse response = null;

		try {
			HttpPost request = new HttpPost(url);
			request.setEntity(new ByteArrayEntity(
					mapper.writeValueAsBytes(body), ContentType.APPLICATION_JSON));
			
			response = doHttpRequest(request);
			HttpEntity entity = response.getEntity();
			T retval = mapper.readValue(entity.getContent(), type);
			return retval;
		} catch (JsonProcessingException jpe) {
			throw new ClientException(jpe.getMessage());
		} catch (IOException ioe) {
			throw new ClientException(ioe.getMessage());
		} catch (EntityNotFoundException enfe) {
			return null;
		} finally {
			closeSafe(response);
		}
	}
	
	
	protected void doDeleteRequest(String url) {
		log.debug("[doDeleteRequest] url={}", url);
		
		CloseableHttpResponse response = null;
		
		try {
			HttpDelete request = new HttpDelete(url);
			response = doHttpRequest(request);
		} catch (EntityNotFoundException enfe) {
			
		} catch (ClientException ce) {
			
		} finally {
			closeSafe(response);
		}
	}
	
	
	protected CloseableHttpResponse doHttpRequest(HttpUriRequest request) throws EntityNotFoundException, ClientException {
		
		CloseableHttpResponse response = null;
		
		try {
			response = this.httpClient.execute(request);
			
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
				response.close();
				throw new EntityNotFoundException("API returned 404 on request GET " + request.getURI());
			}
			
			return response;
		
		} catch (ClientProtocolException cpe) {
			throw new ClientException(cpe.getMessage());
		} catch (IOException ioe) {
			throw new ClientException(ioe.getMessage());
		}
	}
	
	
	protected InputStream doImageRequest(String url) {
		
		HttpGet request = new HttpGet(url);
		request.addHeader(HttpHeaders.ACCEPT, "image/png,image/jpeg");
		try {
			CloseableHttpResponse response = this.httpClient.execute(request);
			return response.getEntity().getContent();
		} catch (Exception ex) {
			
		}
		return null;
	}

	
	private final void closeSafe(CloseableHttpResponse response) {
		if (response != null) {
			try {
				response.close();
			} catch (IOException ex) {
				// noop
			}
		}
	}
	
	
	protected final String fromTokens(Object... o) {
		StringBuilder b = new StringBuilder(DEFAULT_BASE_URL);
		for (Object t : o) {
			b.append(t.toString());
		}
		return b.toString();
	}
	
	
	protected final String fromTokensAndPage(PageRequest page, Object...objects) {
		
		StringBuilder b = new StringBuilder(DEFAULT_BASE_URL);
		for (Object o : objects) {
			b.append(o.toString());
		}

		if (b.toString().contains("?")) {
			b.append("&page=");
		} else {
			b.append("?page=");
		}
		
		if (page == null) {
			b.append(PageRequest.DEFAULT_PAGE);
		} else {
			b.append(page.getPage());
		}
		
		b.append("&per_page=");
		if (page == null) {
			b.append(PageRequest.DEFAULT_PAGE_SIZE);
		} else {
			b.append(page.getSize());
		}
		
		return b.toString();
	}

}
