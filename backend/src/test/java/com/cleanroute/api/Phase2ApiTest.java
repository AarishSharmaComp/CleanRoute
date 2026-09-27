package com.cleanroute.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.cleanroute.repository.AppUserRepository;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:phase2-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","app.jwt.secret=test-signing-secret-that-is-more-than-32-bytes-long"})
@AutoConfigureMockMvc
class Phase2ApiTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired AppUserRepository users;
 private String register(String email)throws Exception {MvcResult r=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\"correct-horse-battery\",\"displayName\":\"Test User\"}")).andExpect(status().isCreated()).andReturn();return mapper.readTree(r.getResponse().getContentAsString()).get("token").asText();}
 @Test void registrationHashesPasswordLoginWorksAndProtectedUserRequiresToken()throws Exception {
  mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized()); String token=register("alice@example.test");
  MvcResult saved=mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("alice@example.test")).andExpect(jsonPath("$.preferences.preferredTravelMode").value("WALK")).andReturn();
  JsonNode user=mapper.readTree(saved.getResponse().getContentAsString());assertThat(user.has("passwordHash")).isFalse();
  var stored=users.findByEmail("alice@example.test").orElseThrow();assertThat(stored.getPasswordHash()).isNotEqualTo("correct-horse-battery").startsWith("$2");
  mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"alice@example.test\",\"password\":\"correct-horse-battery\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
  mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"alice@example.test\",\"password\":\"wrong-password\"}")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"alice@example.test\",\"password\":\"correct-horse-battery\",\"displayName\":\"Again\"}")).andExpect(status().isConflict());
 }
 @Test void placesAndRoutesAreOwnedByTheirCreator()throws Exception {
  String one=register("owner1@example.test"),two=register("owner2@example.test");
  MvcResult place=mvc.perform(post("/api/places").header("Authorization","Bearer "+one).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Home\",\"latitude\":12.9,\"longitude\":77.6}")).andExpect(status().isOk()).andReturn();
  String placeId=mapper.readTree(place.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(get("/api/places").header("Authorization","Bearer "+two)).andExpect(status().isOk()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
  mvc.perform(delete("/api/places/"+placeId).header("Authorization","Bearer "+two)).andExpect(status().isNotFound());
  mvc.perform(delete("/api/places/"+placeId).header("Authorization","Bearer "+one)).andExpect(status().isNoContent());
  String routeBody="{\"originName\":\"A\",\"originLatitude\":12.9,\"originLongitude\":77.6,\"destinationName\":\"B\",\"destinationLatitude\":13.0,\"destinationLongitude\":77.7,\"travelMode\":\"CYCLE\",\"routePreference\":\"CYCLIST\"}";
  MvcResult route=mvc.perform(post("/api/routes/save").header("Authorization","Bearer "+one).contentType(MediaType.APPLICATION_JSON).content(routeBody)).andExpect(status().isOk()).andReturn();String routeId=mapper.readTree(route.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(get("/api/routes/saved").header("Authorization","Bearer "+two)).andExpect(status().isOk()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
  mvc.perform(delete("/api/routes/saved/"+routeId).header("Authorization","Bearer "+two)).andExpect(status().isNotFound());
 }
}
