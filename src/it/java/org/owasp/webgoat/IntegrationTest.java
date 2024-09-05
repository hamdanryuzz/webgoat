package org.owasp.webgoat;

import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.Map;
import lombok.Getter;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;

public abstract class IntegrationTest {

  private static String webGoatPort = System.getenv().getOrDefault("WEBGOAT_PORT", "8080");
  private static String webGoatContext =
      System.getenv().getOrDefault("WEBGOAT_CONTEXT", "/WebGoat/");

  @Getter private static String webWolfPort = System.getenv().getOrDefault("WEBWOLF_PORT", "9090");

  @Getter
  private static String webWolfHost = System.getenv().getOrDefault("WEBWOLF_HOST", "127.0.0.1");

  @Getter
  private static String webGoatHost = System.getenv().getOrDefault("WEBGOAT_HOST", "127.0.0.1");

  private static String webWolfContext =
      System.getenv().getOrDefault("WEBWOLF_CONTEXT", "/WebWolf/");

  private static boolean useSSL =
      Boolean.valueOf(System.getenv().getOrDefault("WEBGOAT_SSLENABLED", "false"));
  private static String webgoatUrl =
      (useSSL ? "https://" : "http://") + webGoatHost + ":" + webGoatPort + webGoatContext;
  private static String webWolfUrl = "http://" + webWolfHost + ":" + webWolfPort + webWolfContext;
  @Getter private String webGoatCookie;
  @Getter private String webWolfCookie;
  @Getter private final String user = "webgoat";

  protected String url(String url) {
    return webgoatUrl + url;
  }

  protected String webWolfUrl(String url) {
    return webWolfUrl + url;
  }

  protected String webWolfFileUrl(String fileName) {
    return webWolfUrl("files") + "/" + getUser() + "/" + fileName;
  }

  @BeforeEach
  public void login() {
    String location =
        given()
            .when()
            .relaxedHTTPSValidation()
            .formParam("username", user)
            .formParam("password", "password")
            .post(url("login"))
            .then()
            .cookie("JSESSIONID")
            .statusCode(302)
            .extract()
            .header("Location");
    if (location.endsWith("?error")) {
      webGoatCookie =
          RestAssured.given()
              .when()
              .relaxedHTTPSValidation()
              .formParam("username", user)
              .formParam("password", "password")
              .formParam("matchingPassword", "password")
              .formParam("agree", "agree")
              .post(url("register.mvc"))
              .then()
              .cookie("JSESSIONID")
              .statusCode(302)
              .extract()
              .cookie("JSESSIONID");
    } else {
      webGoatCookie =
          given()
              .when()
              .relaxedHTTPSValidation()
              .formParam("username", user)
              .formParam("password", "password")
              .post(url("login"))
              .then()
              .cookie("JSESSIONID")
              .statusCode(302)
              .extract()
              .cookie("JSESSIONID");
    }

    webWolfCookie =
        RestAssured.given()
            .when()
            .relaxedHTTPSValidation()
            .formParam("username", user)
            .formParam("password", "password")
            .post(webWolfUrl("login"))
            .then()
            .statusCode(302)
            .cookie("WEBWOLFSESSION")
            .extract()
            .cookie("WEBWOLFSESSION");
  }

  @AfterEach
  public void logout() {
    RestAssured.given().when().relaxedHTTPSValidation().get(url("logout")).then().statusCode(200);
  }

  public void startLesson(String lessonName) {
    startLesson(lessonName, false);
  }

  public void startLesson(String lessonName, boolean restart) {
    RestAssured.given()
        .when()
        .relaxedHTTPSValidation()
        .cookie("JSESSIONID", getWebGoatCookie())
        .get(url(lessonName + ".lesson.lesson"))
        .then()
        .statusCode(200);

    if (restart) {
      RestAssured.given()
          .when()
          .relaxedHTTPSValidation()
          .cookie("JSESSIONID", getWebGoatCookie())
          .get(url("service/restartlesson.mvc"))
          .then()
          .statusCode(200);
    }
  }

  public void checkAssignment(String url, Map<String, ?> params, boolean expectedResult) {
    MatcherAssert.assertThat(
        RestAssured.given()
            .when()
            .relaxedHTTPSValidation()
            .cookie("JSESSIONID", getWebGoatCookie())
            .formParams(params)
            .post(url)
            .then()
            .statusCode(200)
            .extract()
            .path("lessonCompleted"),
        CoreMatchers.is(expectedResult));
  }

  public void checkAssignmentWithPUT(String url, Map<String, ?> params, boolean expectedResult) {
    MatcherAssert.assertThat(
        RestAssured.given()
            .when()
            .relaxedHTTPSValidation()
            .cookie("JSESSIONID", getWebGoatCookie())
            .formParams(params)
            .put(url)
            .then()
            .statusCode(200)
            .extract()
            .path("lessonCompleted"),
        CoreMatchers.is(expectedResult));
  }

  // TODO is prefix useful? not every lesson endpoint needs to start with a certain prefix (they are
  // only required to be in the same package)
  public void checkResults(String prefix) {
    checkResults();

    MatcherAssert.assertThat(
        RestAssured.given()
            .when()
            .relaxedHTTPSValidation()
            .cookie("JSESSIONID", getWebGoatCookie())
            .get(url("service/lessonoverview.mvc"))
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("assignment.path"),
        CoreMatchers.everyItem(CoreMatchers.startsWith(prefix)));
  }

  public void checkResults() {
    var result =
        RestAssured.given()
            .when()
            .relaxedHTTPSValidation()
            .cookie("JSESSIONID", getWebGoatCookie())
            .get(url("service/lessonoverview.mvc"))
            .andReturn();

    MatcherAssert.assertThat(
        result.then().statusCode(200).extract().jsonPath().getList("solved"),
        CoreMatchers.everyItem(CoreMatchers.is(true)));
  }

  public void checkAssignment(
      String url, ContentType contentType, String body, boolean expectedResult) {
    MatcherAssert.assertThat(
        RestAssured.given()
            .when()
            .relaxedHTTPSValidation()
            .contentType(contentType)
            .cookie("JSESSIONID", getWebGoatCookie())
            .body(body)
            .post(url)
            .then()
            .statusCode(200)
            .extract()
            .path("lessonCompleted"),
        CoreMatchers.is(expectedResult));
  }

  public void checkAssignmentWithGet(String url, Map<String, ?> params, boolean expectedResult) {
    MatcherAssert.assertThat(
        RestAssured.given()
            .when()
            .relaxedHTTPSValidation()
            .cookie("JSESSIONID", getWebGoatCookie())
            .queryParams(params)
            .get(url)
            .then()
            .statusCode(200)
            .extract()
            .path("lessonCompleted"),
        CoreMatchers.is(expectedResult));
  }

  public String getWebWolfFileServerLocation() {
    String result =
        RestAssured.given()
            .when()
            .relaxedHTTPSValidation()
            .cookie("WEBWOLFSESSION", getWebWolfCookie())
            .get(webWolfUrl("file-server-location"))
            .then()
            .extract()
            .response()
            .getBody()
            .asString();
    result = result.replace("%20", " ");
    return result;
  }

  public String webGoatServerDirectory() {
    return RestAssured.given()
        .when()
        .relaxedHTTPSValidation()
        .cookie("JSESSIONID", getWebGoatCookie())
        .get(url("server-directory"))
        .then()
        .extract()
        .response()
        .getBody()
        .asString();
  }

  public void cleanMailbox() {
    RestAssured.given()
        .when()
        .relaxedHTTPSValidation()
        .cookie("WEBWOLFSESSION", getWebWolfCookie())
        .delete(webWolfUrl("mail"))
        .then()
        .statusCode(HttpStatus.ACCEPTED.value());
  }
}
