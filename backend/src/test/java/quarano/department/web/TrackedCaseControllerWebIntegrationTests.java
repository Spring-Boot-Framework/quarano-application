/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package quarano.department.web;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.CoreMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static quarano.department.web.TrackedCaseLinkRelations.*;

import lombok.RequiredArgsConstructor;
import quarano.QuaranoWebIntegrationTest;
import quarano.ValidationUtils;
import quarano.WithQuaranoUser;
import quarano.department.TrackedCaseDataInitializer;
import quarano.department.TrackedCaseProperties;
import quarano.department.TrackedCaseRepository;
import quarano.department.web.TrackedCaseRepresentations.CommentInput;
import quarano.department.web.TrackedCaseRepresentations.TrackedCaseDto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.hateoas.client.LinkDiscoverer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;

/**
 * @author Oliver Drotbohm
 */
@WithQuaranoUser("agent1")
@QuaranoWebIntegrationTest
@RequiredArgsConstructor
class TrackedCaseControllerWebIntegrationTests {

	private final MockMvc mvc;
	private final ObjectMapper jackson;
	private final TrackedCaseProperties configuration;
	private final ValidationUtils validator;
	private final TrackedCaseRepository cases;
	private final TrackedCaseRepresentations representations;
	private final MessageSourceAccessor messages;
	private final LinkDiscoverer discoverer;

	@Test
	void successfullyCreatesNewTrackedCase() throws Exception {

		var payload = createMinimalPayload();

		var response = issueCaseCreation(payload).getContentAsString();
		var document = JsonPath.parse(response);

		assertMinimalFieldsSet(document, payload);
		assertThat(discoverer.findLinkWithRel(CONCLUDE, response)).isPresent();
	}

	@Test
	void indicateStartTrackingIfRequiredDataIsSet() throws Exception {

		var payload = createMinimalPayload() //
				.setEmail("foo@bar.com") //
				.setDateOfBirth(LocalDate.now().minusYears(25));

		var response = issueCaseCreation(payload).getContentAsString();
		var document = JsonPath.parse(response);

		assertMinimalFieldsSet(document, payload);

		Stream.of(START_TRACKING, CONCLUDE).forEach(it -> {
			assertThat(discoverer.findLinkWithRel(it, response)).isPresent();
		});
	}

	@Test
	@SuppressWarnings("null")
	void updatesCaseWithMinimalPayload() throws Exception {

		var payload = createMinimalPayload();
		var response = issueCaseCreation(payload);

		String location = response.getHeader(HttpHeaders.LOCATION);

		response = mvc.perform(put(location) //
				.content(jackson.writeValueAsString(payload.setInfected(true))) //
				.contentType(MediaType.APPLICATION_JSON)) //
				.andExpect(status().isOk()) //
				.andReturn().getResponse();

		var document = JsonPath.parse(response.getContentAsString());

		assertMinimalFieldsSet(document, payload);
		assertThat(document.read("$.infected", boolean.class)).isTrue();
	}

	@Test
	void rejectsCaseCreationWithoutRequiredFields() throws Exception {

		var response = expectBadRequest(HttpMethod.POST, "/api/hd/cases", new TrackedCaseDto());

		validator.getRequiredProperties(TrackedCaseDto.class) //
				.forEach(it -> {
					assertThat(response.read("$." + it, String.class)).isNotNull();
				});
	}

	@Test
	void rejectsInvalidCharactersForStringFields() throws Exception {

		var today = LocalDate.now();
		var payload = new TrackedCaseDto() //
				.setFirstName("Michael 123") //
				.setLastName("Mustermann 123") //
				.setTestDate(today) //
				.setQuarantineStartDate(today) //
				.setQuarantineEndDate(today.plus(configuration.getQuarantinePeriod())) //
				.setPhone("0123456789") //
				.setCity("city 123") //
				.setStreet("\\") //
				.setHouseNumber("-");

		var document = expectBadRequest(HttpMethod.POST, "/api/hd/cases", payload);

		var alphabetic = messages.getMessage("Alphabetic");
		var alphaNumeric = messages.getMessage("AlphaNumeric");

		assertThat(document.read("$.firstName", String.class)).isEqualTo(alphabetic);
		assertThat(document.read("$.lastName", String.class)).isEqualTo(alphabetic);
		assertThat(document.read("$.city", String.class)).contains("gültige Stadt");

		assertThat(document.read("$.street", String.class)).contains("gültige Straße");
		assertThat(document.read("$.houseNumber", String.class)).isEqualTo(alphaNumeric);
	}

	@Test
	void rejectsEmptyTrackedPersonDetailsIfEnrollmentDone() throws Exception {

		var trackedCase = cases.findById(TrackedCaseDataInitializer.TRACKED_CASE_SANDRA).orElseThrow();

		@SuppressWarnings("null")
		var payload = representations.toInputRepresentation(trackedCase) //
				.setEmail(null) //
				.setDateOfBirth(null);

		var document = expectBadRequest(HttpMethod.PUT, "/api/hd/cases/" + trackedCase.getId(), payload);

		assertThat(document.read("$.email", String.class)).isNotNull();
	}

	@Test
	void getAllCasesOrderedCorrectly() throws Exception {

		var response = mvc.perform(get("/api/hd/cases") //
				.contentType(MediaType.APPLICATION_JSON)) //
				.andExpect(status().isOk()) //
				.andReturn().getResponse().getContentAsString();

		var document = JsonPath.parse(response);

		var lastnamesFromResponse = List.of( //
				document.read("$._embedded.cases[0].lastName", String.class), //
				document.read("$._embedded.cases[1].lastName", String.class), //
				document.read("$._embedded.cases[2].lastName", String.class));

		var expectedList = new ArrayList<>(lastnamesFromResponse);
		Collections.sort(expectedList);

		assertThat(lastnamesFromResponse).containsExactlyElementsOf(expectedList);
	}

	@Test
	void addsComment() throws Exception {

		var trackedCase = cases.findById(TrackedCaseDataInitializer.TRACKED_CASE_GUSTAV).orElseThrow();

		var payload = new CommentInput();
		payload.setComment("Kommentar!");

		var response = mvc.perform(post("/api/hd/cases/{id}/comments", trackedCase.getId()) //
				.content(jackson.writeValueAsString(payload)) //
				.contentType(MediaType.APPLICATION_JSON)) //
				.andExpect(status().isOk()) //
				.andReturn().getResponse().getContentAsString();

		var document = JsonPath.parse(response);

		assertThat(document.read("$.comments[0].comment", String.class)).isEqualTo(payload.getComment());
		assertThat(document.read("$.comments[0].date", String.class)).isNotBlank();
		assertThat(document.read("$.comments[0].author", String.class)).isNotBlank();
	}

	private ReadContext expectBadRequest(HttpMethod method, String uri, Object payload) throws Exception {

		return JsonPath.parse(mvc.perform(request(method, uri) //
				.content(jackson.writeValueAsString(payload)) //
				.contentType(MediaType.APPLICATION_JSON)) //
				.andExpect(status().isBadRequest()) //
				.andReturn().getResponse().getContentAsString());
	}

	private TrackedCaseDto createMinimalPayload() {

		var today = LocalDate.now();

		return new TrackedCaseDto() //
				.setFirstName("Michael") //
				.setLastName("Mustermann") //
				.setEmail("") // empty email to verify it gets bound to null and does not trigger validation
				.setTestDate(today) //
				.setQuarantineStartDate(today) //
				.setQuarantineEndDate(today.plus(configuration.getQuarantinePeriod())) //
				.setPhone("0123456789");
	}

	private MockHttpServletResponse issueCaseCreation(TrackedCaseDto payload) throws Exception {

		return mvc.perform(post("/api/hd/cases") //
				.content(jackson.writeValueAsString(payload)) //
				.contentType(MediaType.APPLICATION_JSON)) //
				.andExpect(status().isCreated()) //
				.andExpect(header().string(HttpHeaders.LOCATION, is(notNullValue()))) //
				.andReturn().getResponse();
	}

	private static void assertMinimalFieldsSet(DocumentContext document, TrackedCaseDto payload) {

		assertThat(document.read("$.firstName", String.class)).isEqualTo(payload.getFirstName());
		assertThat(document.read("$.lastName", String.class)).isEqualTo(payload.getLastName());
		assertThat(document.read("$.quarantineStartDate", String.class)) //
				.isEqualTo(payload.getQuarantineStartDate().toString());
		assertThat(document.read("$.quarantineEndDate", String.class)) //
				.isEqualTo(payload.getQuarantineEndDate().toString());
		assertThat(document.read("$.phone", String.class)).isEqualTo(payload.getPhone());
		assertThat(document.read("$.testDate", String.class)).isEqualTo(payload.getTestDate().toString());
	}
}
