package com.taskmanager;

import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TaskManagerTest {

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static String baseUrl;

    private static final By INPUT_FIELD   = By.id("taskInput");
    private static final By ADD_BUTTON    = By.id("addBtn");
    private static final By TASK_LIST     = By.id("taskList");
    private static final By APP_HEADING   = By.id("appHeading");
    private static final By TASK_ITEMS    = By.cssSelector("#taskList .task-item");
    private static final By TASK_TEXT     = By.cssSelector(".task-text");
    private static final By DONE_BUTTON   = By.cssSelector(".done-btn");
    private static final By DELETE_BUTTON = By.cssSelector(".delete-btn");

    @BeforeAll
    void initDriver() {
        baseUrl = resolveBaseUrl();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                "--disable-gpu", "--window-size=1920,1080", "--remote-allow-origins=*");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterAll
    void shutdownDriver() { if (driver != null) driver.quit(); }

    @BeforeEach
    void openHomeAndClear() {
        driver.get(baseUrl + "/");
        wait.until(ExpectedConditions.visibilityOfElementLocated(INPUT_FIELD));
        clearAllTasksViaUI();
    }

    @Test @Order(1) @DisplayName("01 - Home page loads")
    void testHomePageLoads() {
        driver.get(baseUrl + "/");
        assertTrue(driver.getPageSource().length() > 0);
        assertTrue(driver.findElement(By.tagName("body")).isDisplayed());
    }

    @Test @Order(2) @DisplayName("02 - Page title contains 'task'")
    void testPageTitle() {
        driver.get(baseUrl + "/");
        assertTrue(driver.getTitle().toLowerCase().contains("task"));
    }

    @Test @Order(3) @DisplayName("03 - Heading visible")
    void testHeadingVisible() {
        WebElement h = wait.until(ExpectedConditions.visibilityOfElementLocated(APP_HEADING));
        assertTrue(h.isDisplayed());
        assertFalse(h.getText().isBlank());
    }

    @Test @Order(4) @DisplayName("04 - Input field exists")
    void testTaskInputFieldExists() {
        WebElement i = wait.until(ExpectedConditions.elementToBeClickable(INPUT_FIELD));
        assertTrue(i.isDisplayed() && i.isEnabled());
    }

    @Test @Order(5) @DisplayName("05 - Add button exists")
    void testAddButtonExists() {
        WebElement b = wait.until(ExpectedConditions.elementToBeClickable(ADD_BUTTON));
        assertTrue(b.isDisplayed() && b.isEnabled());
    }

    @Test @Order(6) @DisplayName("06 - Task list container exists")
    void testTaskListContainerExists() {
        assertNotNull(wait.until(ExpectedConditions.presenceOfElementLocated(TASK_LIST)));
    }

    @Test @Order(7) @DisplayName("07 - Add single task")
    void testAddSingleTask() {
        String t = "Buy " + shortId();
        addTask(t);
        assertTrue(taskExistsInList(t));
    }

    @Test @Order(8) @DisplayName("08 - Task text matches input")
    void testAddedTaskTextMatches() {
        String t = "Write " + shortId();
        addTask(t);
        WebElement first = wait.until(ExpectedConditions.visibilityOfElementLocated(TASK_ITEMS));
        assertEquals(t, first.findElement(TASK_TEXT).getText().trim());
    }

    @Test @Order(9) @DisplayName("09 - Add multiple tasks")
    void testAddMultipleTasks() {
        String[] arr = { "A " + shortId(), "B " + shortId(), "C " + shortId() };
        for (String t : arr) addTask(t);
        for (String t : arr) assertTrue(taskExistsInList(t));
    }

    @Test @Order(10) @DisplayName("10 - Input clears after add")
    void testInputClearsAfterAdd() {
        addTask("Clear " + shortId());
        assertEquals("", driver.findElement(INPUT_FIELD).getAttribute("value"));
    }

    @Test @Order(11) @DisplayName("11 - Empty task rejected")
    void testEmptyTaskRejected() {
        int before = driver.findElements(TASK_ITEMS).size();
        driver.findElement(INPUT_FIELD).clear();
        driver.findElement(ADD_BUTTON).click();
        sleep(500);
        assertEquals(before, driver.findElements(TASK_ITEMS).size());
    }

    @Test @Order(12) @DisplayName("12 - Mark task done")
    void testMarkTaskDone() {
        String t = "Done " + shortId();
        addTask(t);
        findTaskItem(t).findElement(DONE_BUTTON).click();
        wait.until(d -> findTaskItem(t).getAttribute("class").contains("done"));
        assertTrue(findTaskItem(t).getAttribute("class").contains("done"));
    }

    @Test @Order(13) @DisplayName("13 - Done has strikethrough")
    void testDoneTaskHasStrikethrough() {
        String t = "Strike " + shortId();
        addTask(t);
        findTaskItem(t).findElement(DONE_BUTTON).click();
        wait.until(d -> findTaskItem(t).getAttribute("class").contains("done"));
        String dec = findTaskItem(t).findElement(TASK_TEXT).getCssValue("text-decoration");
        assertTrue(dec.contains("line-through"));
    }

    @Test @Order(14) @DisplayName("14 - Toggle done off")
    void testUnmarkTaskDone() {
        String t = "Toggle " + shortId();
        addTask(t);
        findTaskItem(t).findElement(DONE_BUTTON).click();
        wait.until(d -> findTaskItem(t).getAttribute("class").contains("done"));
        findTaskItem(t).findElement(DONE_BUTTON).click();
        wait.until(d -> !findTaskItem(t).getAttribute("class").contains("done"));
        assertFalse(findTaskItem(t).getAttribute("class").contains("done"));
    }

    @Test @Order(15) @DisplayName("15 - Delete task")
    void testDeleteTask() {
        String t = "Del " + shortId();
        addTask(t);
        findTaskItem(t).findElement(DELETE_BUTTON).click();
        wait.until(d -> !taskExistsInList(t));
        assertFalse(taskExistsInList(t));
    }

    @Test @Order(16) @DisplayName("16 - Count drops by one after delete")
    void testTaskCountAfterDeletion() {
        addTask("X " + shortId());
        addTask("Y " + shortId());
        addTask("Z " + shortId());
        int before = driver.findElements(TASK_ITEMS).size();
        driver.findElements(TASK_ITEMS).get(0).findElement(DELETE_BUTTON).click();
        wait.until(d -> driver.findElements(TASK_ITEMS).size() == before - 1);
        assertEquals(before - 1, driver.findElements(TASK_ITEMS).size());
    }

    @Test @Order(17) @DisplayName("17 - Persists after reload")
    void testTasksPersistAfterReload() {
        String t = "Persist " + shortId();
        addTask(t);
        driver.navigate().refresh();
        wait.until(ExpectedConditions.visibilityOfElementLocated(TASK_LIST));
        assertTrue(taskExistsInList(t));
    }

    @Test @Order(18) @DisplayName("18 - Special characters preserved")
    void testSpecialCharactersHandled() {
        String t = "Edge <case> & \"q\" " + shortId();
        addTask(t);
        assertEquals(t, findTaskItem(t).findElement(TASK_TEXT).getText().trim());
    }

    @Test @Order(19) @DisplayName("19 - /health returns 200")
    void testHealthEndpoint() throws Exception {
        URL url = new URL(baseUrl + "/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        conn.disconnect();
        assertEquals(200, code);
    }

    private static String resolveBaseUrl() {
        String prop = System.getProperty("app.url");
        if (prop != null && !prop.isBlank() && !prop.equals("${app.url}")) return prop.trim();
        String env = System.getenv("APP_URL");
        if (env != null && !env.isBlank()) return env.trim();
        return "http://localhost:8081";
    }

    private void addTask(String text) {
        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(INPUT_FIELD));
        input.clear();
        input.sendKeys(text);
        driver.findElement(ADD_BUTTON).click();
        wait.until(d -> taskExistsInList(text));
    }

    private boolean taskExistsInList(String text) {
        for (WebElement el : driver.findElements(TASK_ITEMS)) {
            try { if (el.findElement(TASK_TEXT).getText().trim().equals(text)) return true; }
            catch (Exception ignored) {}
        }
        return false;
    }

    private WebElement findTaskItem(String text) {
        for (WebElement el : driver.findElements(TASK_ITEMS)) {
            try { if (el.findElement(TASK_TEXT).getText().trim().equals(text)) return el; }
            catch (Exception ignored) {}
        }
        throw new AssertionError("Task not found: " + text);
    }

    private void clearAllTasksViaUI() {
        int safety = 50;
        while (safety-- > 0) {
            List<WebElement> items = driver.findElements(TASK_ITEMS);
            if (items.isEmpty()) return;
            try { items.get(0).findElement(DELETE_BUTTON).click(); sleep(150); }
            catch (Exception e) { return; }
        }
    }

    private static String shortId() { return UUID.randomUUID().toString().substring(0, 6); }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
}
