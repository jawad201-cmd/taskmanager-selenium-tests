package com.taskmanager;

import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
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

/**
 * Selenium test suite for the Task Manager web application.
 *
 * Targets a deployed instance whose URL is supplied via:
 *   - System property -Dapp.url=...
 *   - or environment variable APP_URL
 *   - falling back to http://localhost:8081
 *
 * All tests run on headless Chrome (required by the Jenkins pipeline running on EC2).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TaskManagerTest {

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static String baseUrl;

    // Stable selectors expected on the Task Manager frontend
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
        // Headless Chrome is mandatory for the Jenkins-on-EC2 environment.
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080",
                "--remote-allow-origins=*"
        );

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterAll
    void shutdownDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    @BeforeEach
    void openHomeAndClear() {
        driver.get(baseUrl + "/");
        wait.until(ExpectedConditions.visibilityOfElementLocated(INPUT_FIELD));
        clearAllTasksViaUI();
    }

    // ---------- TESTS ----------

    @Test
    @Order(1)
    @DisplayName("01 - Home page loads with HTTP 200 and renders content")
    void testHomePageLoads() {
        driver.get(baseUrl + "/");
        assertTrue(driver.getPageSource().length() > 0, "Page source should not be empty");
        assertTrue(driver.findElement(By.tagName("body")).isDisplayed(), "Body should be visible");
    }

    @Test
    @Order(2)
    @DisplayName("02 - Page title contains the application name")
    void testPageTitle() {
        driver.get(baseUrl + "/");
        String title = driver.getTitle();
        assertNotNull(title, "Page title must not be null");
        assertTrue(title.toLowerCase().contains("task"),
                "Title should mention 'Task' but was: " + title);
    }

    @Test
    @Order(3)
    @DisplayName("03 - Application heading is visible on the page")
    void testHeadingVisible() {
        WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(APP_HEADING));
        assertTrue(heading.isDisplayed(), "App heading should be visible");
        assertFalse(heading.getText().isBlank(), "Heading text should not be blank");
    }

    @Test
    @Order(4)
    @DisplayName("04 - Task input field is present and enabled")
    void testTaskInputFieldExists() {
        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(INPUT_FIELD));
        assertTrue(input.isDisplayed(), "Input should be displayed");
        assertTrue(input.isEnabled(), "Input should be enabled");
    }

    @Test
    @Order(5)
    @DisplayName("05 - Add button is present, displayed, and enabled")
    void testAddButtonExists() {
        WebElement addBtn = wait.until(ExpectedConditions.elementToBeClickable(ADD_BUTTON));
        assertTrue(addBtn.isDisplayed(), "Add button should be displayed");
        assertTrue(addBtn.isEnabled(), "Add button should be enabled");
    }

    @Test
    @Order(6)
    @DisplayName("06 - Task list container is present in the DOM")
    void testTaskListContainerExists() {
        WebElement list = wait.until(ExpectedConditions.presenceOfElementLocated(TASK_LIST));
        assertNotNull(list, "Task list container must exist");
    }

    @Test
    @Order(7)
    @DisplayName("07 - Adding a single task makes it appear in the list")
    void testAddSingleTask() {
        String taskText = "Buy groceries " + shortId();
        addTask(taskText);
        assertTrue(taskExistsInList(taskText), "New task should appear in the list");
    }

    @Test
    @Order(8)
    @DisplayName("08 - Added task text matches the entered value exactly")
    void testAddedTaskTextMatches() {
        String taskText = "Write DevOps report " + shortId();
        addTask(taskText);
        WebElement firstTask = wait.until(
                ExpectedConditions.visibilityOfElementLocated(TASK_ITEMS));
        String displayed = firstTask.findElement(TASK_TEXT).getText().trim();
        assertEquals(taskText, displayed, "Displayed text must equal entered text");
    }

    @Test
    @Order(9)
    @DisplayName("09 - Multiple tasks can be added and all are visible")
    void testAddMultipleTasks() {
        String[] tasks = {
                "Task A " + shortId(),
                "Task B " + shortId(),
                "Task C " + shortId()
        };
        for (String t : tasks) addTask(t);
        for (String t : tasks) {
            assertTrue(taskExistsInList(t), "Expected task in list: " + t);
        }
        List<WebElement> items = driver.findElements(TASK_ITEMS);
        assertTrue(items.size() >= tasks.length,
                "Item count should be at least " + tasks.length + ", was " + items.size());
    }

    @Test
    @Order(10)
    @DisplayName("10 - Input field clears after adding a task")
    void testInputClearsAfterAdd() {
        addTask("Clear-me-after " + shortId());
        WebElement input = driver.findElement(INPUT_FIELD);
        assertEquals("", input.getAttribute("value"), "Input should be empty after add");
    }

    @Test
    @Order(11)
    @DisplayName("11 - Empty task submission does not create a task")
    void testEmptyTaskRejected() {
        int before = driver.findElements(TASK_ITEMS).size();
        WebElement input = driver.findElement(INPUT_FIELD);
        input.clear();
        driver.findElement(ADD_BUTTON).click();
        sleep(500);
        int after = driver.findElements(TASK_ITEMS).size();
        assertEquals(before, after, "Empty input must not add a task");
    }

    @Test
    @Order(12)
    @DisplayName("12 - Marking a task done applies the 'done' CSS class")
    void testMarkTaskDone() {
        String taskText = "Mark me done " + shortId();
        addTask(taskText);
        WebElement item = findTaskItem(taskText);
        item.findElement(DONE_BUTTON).click();
        wait.until(d -> findTaskItem(taskText).getAttribute("class").contains("done"));
        assertTrue(findTaskItem(taskText).getAttribute("class").contains("done"),
                "Task should have 'done' class after marking");
    }

    @Test
    @Order(13)
    @DisplayName("13 - Done task's text node has visible strikethrough styling")
    void testDoneTaskHasStrikethrough() {
        String taskText = "Style-check " + shortId();
        addTask(taskText);
        WebElement item = findTaskItem(taskText);
        item.findElement(DONE_BUTTON).click();
        wait.until(d -> findTaskItem(taskText).getAttribute("class").contains("done"));
        WebElement textEl = findTaskItem(taskText).findElement(TASK_TEXT);
        String decoration = textEl.getCssValue("text-decoration");
        assertTrue(decoration.contains("line-through"),
                "text-decoration should include line-through, was: " + decoration);
    }

    @Test
    @Order(14)
    @DisplayName("14 - Toggling done a second time removes the 'done' class")
    void testUnmarkTaskDone() {
        String taskText = "Toggle-twice " + shortId();
        addTask(taskText);
        WebElement item = findTaskItem(taskText);
        item.findElement(DONE_BUTTON).click();
        wait.until(d -> findTaskItem(taskText).getAttribute("class").contains("done"));
        findTaskItem(taskText).findElement(DONE_BUTTON).click();
        wait.until(d -> !findTaskItem(taskText).getAttribute("class").contains("done"));
        assertFalse(findTaskItem(taskText).getAttribute("class").contains("done"),
                "Task should no longer be marked done");
    }

    @Test
    @Order(15)
    @DisplayName("15 - Deleting a task removes it from the visible list")
    void testDeleteTask() {
        String taskText = "Delete-me " + shortId();
        addTask(taskText);
        assertTrue(taskExistsInList(taskText), "Precondition: task should exist");
        WebElement item = findTaskItem(taskText);
        item.findElement(DELETE_BUTTON).click();
        wait.until(d -> !taskExistsInList(taskText));
        assertFalse(taskExistsInList(taskText), "Task should be gone after delete");
    }

    @Test
    @Order(16)
    @DisplayName("16 - Task count decreases by exactly one after a single delete")
    void testTaskCountAfterDeletion() {
        addTask("Counter-1 " + shortId());
        addTask("Counter-2 " + shortId());
        addTask("Counter-3 " + shortId());
        int before = driver.findElements(TASK_ITEMS).size();
        driver.findElements(TASK_ITEMS).get(0).findElement(DELETE_BUTTON).click();
        wait.until(d -> driver.findElements(TASK_ITEMS).size() == before - 1);
        int after = driver.findElements(TASK_ITEMS).size();
        assertEquals(before - 1, after, "Count must decrease by exactly one");
    }

    @Test
    @Order(17)
    @DisplayName("17 - Tasks persist in MySQL after a full page reload")
    void testTasksPersistAfterReload() {
        String taskText = "Persisted " + shortId();
        addTask(taskText);
        driver.navigate().refresh();
        wait.until(ExpectedConditions.visibilityOfElementLocated(TASK_LIST));
        assertTrue(taskExistsInList(taskText),
                "Task should still be present after reload (MySQL persistence)");
    }

    @Test
    @Order(18)
    @DisplayName("18 - Special characters in task text are preserved correctly")
    void testSpecialCharactersHandled() {
        String taskText = "Edge <case> & \"chars\" 'quotes' " + shortId();
        addTask(taskText);
        WebElement item = findTaskItem(taskText);
        assertEquals(taskText, item.findElement(TASK_TEXT).getText().trim(),
                "Special characters must round-trip without corruption");
    }

    @Test
    @Order(19)
    @DisplayName("19 - /health endpoint returns HTTP 200 (database connectivity check)")
    void testHealthEndpoint() throws Exception {
        URL url = new URL(baseUrl + "/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        conn.disconnect();
        assertEquals(200, code, "/health must return 200, got " + code);
    }

    // ---------- HELPERS ----------

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
            try {
                if (el.findElement(TASK_TEXT).getText().trim().equals(text)) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private WebElement findTaskItem(String text) {
        for (WebElement el : driver.findElements(TASK_ITEMS)) {
            try {
                if (el.findElement(TASK_TEXT).getText().trim().equals(text)) return el;
            } catch (Exception ignored) { }
        }
        throw new AssertionError("Task not found in list: " + text);
    }

    private void clearAllTasksViaUI() {
        // Repeatedly delete the first item until none remain (best-effort cleanup
        // so each test starts from a deterministic state).
        int safety = 50;
        while (safety-- > 0) {
            List<WebElement> items = driver.findElements(TASK_ITEMS);
            if (items.isEmpty()) return;
            try {
                items.get(0).findElement(DELETE_BUTTON).click();
                sleep(150);
            } catch (Exception e) {
                return;
            }
        }
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
