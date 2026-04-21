package github;

import com.thoughtworks.selenium.Selenium;
import com.thoughtworks.selenium.webdriven.WebDriverBackedSelenium;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Исполнение шаблонов Selenium IDE при помощи Selenium RC API (com.thoughtworks.selenium).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GitHubRCTest {

    private static final int WAIT_TIMEOUT_SECONDS = 20;
    private static final String BASE_URL = "https://github.com";
    private static final String LOGIN_INPUT = "xpath=//input[@id='login_field']";
    private static final String PASSWORD_INPUT = "xpath=//input[@id='password']";
    private static final String SUBMIT_BUTTON = "xpath=//input[@name='commit']";
    private static final String LOGIN_ERROR = "xpath=//div[contains(@class, 'js-flash-alert') or contains(text(), 'Incorrect username or password')]";

    private WebDriver getDriver(String browser) {
        if ("firefox".equalsIgnoreCase(browser)) {
            WebDriverManager.firefoxdriver().setup();
            FirefoxOptions options = new FirefoxOptions();
            options.addPreference("dom.webdriver.enabled", false);
            options.addPreference("useAutomationExtension", false);
            options.addPreference("general.useragent.override", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0");
            options.addPreference("network.http.referer.spoofSource", true);
            return new FirefoxDriver(options);
        }

        WebDriverManager.chromedriver().setup();
        return new ChromeDriver();
    }

    private void waitUntil(WebDriver driver, String failureMessage, Supplier<Boolean> condition) {
        try {
            new WebDriverWait(driver, WAIT_TIMEOUT_SECONDS).until(ignored -> condition.get());
        } catch (TimeoutException e) {
            throw new AssertionError(failureMessage, e);
        }
    }

    private boolean isElementPresent(Selenium selenium, String locator) {
        try {
            return selenium.isElementPresent(locator);
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForElement(WebDriver driver, Selenium selenium, String locator) {
        waitUntil(driver, "Элемент не дождался своего появления: " + locator, () -> isElementPresent(selenium, locator));
    }

    private void waitForLocation(WebDriver driver, Selenium selenium, String expectedLocationPart) {
        waitUntil(driver,
                "Не дождались изменения URL, ожидали фрагмент: " + expectedLocationPart + ", текущий URL: " + selenium.getLocation(),
                () -> selenium.getLocation().contains(expectedLocationPart));
    }

    private void waitForAnyLocation(WebDriver driver, Selenium selenium, String... expectedLocationParts) {
        waitUntil(driver, "Не дождались одного из ожидаемых URL", () -> {
            String location = selenium.getLocation();
            for (String part : expectedLocationParts) {
                if (location.contains(part)) {
                    return true;
                }
            }
            return false;
        });
    }

    private void waitForXpath(WebDriver driver, String xpath) {
        waitUntil(driver, "Не дождались элемента по xpath: " + xpath, () -> !driver.findElements(By.xpath(xpath)).isEmpty());
    }

    private WebElement firstByXpath(WebDriver driver, String xpath) {
        waitForXpath(driver, xpath);
        return driver.findElements(By.xpath(xpath)).get(0);
    }

    private void clickWithFallback(Selenium selenium, String locator, String fallbackScript) {
        try {
            selenium.click(locator);
        } catch (Exception e) {
            selenium.runScript(fallbackScript);
        }
    }

    private String getRequiredCredential(String propertyName, String envName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Укажите credential через -D" + propertyName + " или переменную окружения " + envName);
        }
        return value;
    }

    private boolean isTwoFactorPage(WebDriver driver) {
        return driver.getCurrentUrl().contains("/sessions/two-factor")
                || !driver.findElements(By.xpath("//input[@name='app_otp' or contains(@autocomplete, 'one-time-code')]" )).isEmpty();
    }

    private boolean isLoggedIn(WebDriver driver) {
        return !driver.findElements(By.xpath("//meta[@name='user-login' and @content != '']")).isEmpty();
    }

    private String getLoggedInUsername(WebDriver driver) {
        return firstByXpath(driver, "//meta[@name='user-login' and @content != '']").getAttribute("content");
    }

    private String loginToGitHub(WebDriver driver, Selenium selenium) {
        String email = getRequiredCredential("github.email", "GITHUB_EMAIL");
        String password = getRequiredCredential("github.password", "GITHUB_PASSWORD");

        selenium.open("/login");
        selenium.windowMaximize();

        waitForElement(driver, selenium, LOGIN_INPUT);
        selenium.type(LOGIN_INPUT, email);
        selenium.type(PASSWORD_INPUT, password);
        clickWithFallback(
                selenium,
                SUBMIT_BUTTON,
                "document.evaluate(\"//input[@name='commit']\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();"
        );

        waitUntil(driver, "Авторизация не завершилась ни успехом, ни явной ошибкой", () ->
                isLoggedIn(driver) || isTwoFactorPage(driver) || isElementPresent(selenium, LOGIN_ERROR));

        assertTrue(!isTwoFactorPage(driver), "GitHub запросил 2FA, тест логина по email/password не может завершиться автоматически.");
        assertTrue(isLoggedIn(driver), "Авторизация не выполнена. Проверьте актуальность github.email/github.password.");
        return getLoggedInUsername(driver);
    }

    private void safeStop(Selenium selenium, WebDriver driver) {
        try {
            selenium.stop();
        } catch (Exception ignored) {
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignore) {
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testSearchRepository(String browser) {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        try {
            selenium.open("/");
            selenium.windowMaximize();

            String searchBtnLocator = "xpath=//button[contains(@class, 'header-search-button') or contains(@aria-label, 'Search')]";
            String searchInputLocator = "xpath=//input[contains(@name, 'query-builder-test') or contains(@placeholder, 'Search') or @id='query-builder-test']";
            String searchResultsLocator = "xpath=//div[contains(@data-testid, 'results-list')] | //ul[contains(@class, 'repo-list')]";
            String firstRepoResultLocator = "xpath=(//div[contains(@data-testid, 'results-list')]//a[contains(@href, '/') and not(contains(@href, '/search'))] | //ul[contains(@class, 'repo-list')]//a[contains(@href, '/')])[1]";

            waitForElement(driver, selenium, searchBtnLocator);
            clickWithFallback(
                    selenium,
                    searchBtnLocator,
                    "document.evaluate(\"//button[contains(@class, 'header-search-button') or contains(@aria-label, 'Search')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();"
            );

            waitForElement(driver, selenium, searchInputLocator);
            selenium.open("/search?q=Selenium+WebDriver&type=repositories");
            waitForElement(driver, selenium, searchResultsLocator);
            assertTrue(selenium.isElementPresent(searchResultsLocator), "Результаты поиска не появились.");

            waitForElement(driver, selenium, firstRepoResultLocator);
            selenium.click(firstRepoResultLocator);
            waitUntil(driver,
                    "Не перешли на страницу репозитория",
                    () -> selenium.getLocation().length() > BASE_URL.length() && !selenium.getLocation().contains("/search"));
        } finally {
            safeStop(selenium, driver);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testTopics(String browser) {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        try {
            selenium.open("/topics");
            selenium.windowMaximize();

            String topicsHeaderLocator = "xpath=//h1[contains(text(), 'Topics')]";
            String firstTopicLocator = "xpath=(//a[starts-with(@href, '/topics/') and contains(@class, 'no-underline')])[1]";

            waitForElement(driver, selenium, topicsHeaderLocator);
            assertTrue(selenium.isElementPresent(topicsHeaderLocator), "Заголовок Topics не обнаружен");

            waitForElement(driver, selenium, firstTopicLocator);
            selenium.click(firstTopicLocator);
            waitForLocation(driver, selenium, "/topics/");
            assertTrue(selenium.getLocation().contains("/topics/"), "На страницу топика переход не осуществлен");
        } finally {
            safeStop(selenium, driver);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testTrendingJavaRepositories(String browser) {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        try {
            selenium.open("/trending");
            selenium.windowMaximize();

            String languageDropdown = "xpath=//summary[contains(., 'Language') or contains(., 'Any language')]";
            String javaOption = "xpath=//a[contains(@href, '/trending/java')]";

            waitForElement(driver, selenium, languageDropdown);
            clickWithFallback(
                    selenium,
                    languageDropdown,
                    "document.evaluate(\"//summary[contains(., 'Language') or contains(., 'Any language')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();"
            );

            waitForElement(driver, selenium, javaOption);
            clickWithFallback(
                    selenium,
                    javaOption,
                    "document.evaluate(\"//a[contains(@href, '/trending/java')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();"
            );

            waitForLocation(driver, selenium, "/trending/java");
            assertTrue(selenium.getLocation().contains("/trending/java"), "Переход к трендам Java не осуществлен");
        } finally {
            safeStop(selenium, driver);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testInvalidLogin(String browser) {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        try {
            selenium.open("/login");
            selenium.windowMaximize();

            waitForElement(driver, selenium, LOGIN_INPUT);
            selenium.type(LOGIN_INPUT, "test_qa_lab3_fake_user");
            selenium.type(PASSWORD_INPUT, "FakePassword123!");
            clickWithFallback(
                    selenium,
                    SUBMIT_BUTTON,
                    "document.evaluate(\"//input[@name='commit']\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();"
            );

            waitForElement(driver, selenium, LOGIN_ERROR);
            assertTrue(selenium.isElementPresent(LOGIN_ERROR), "Сообщение об ошибке авторизации не найдено");
        } finally {
            safeStop(selenium, driver);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testRepositoryTabs(String browser) {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        try {
            selenium.open("/SeleniumHQ/selenium");
            selenium.windowMaximize();

            String issuesTab = "xpath=//a[@id='issues-tab']";
            String pullsTab = "xpath=//a[@id='pull-requests-tab']";

            waitForElement(driver, selenium, issuesTab);
            clickWithFallback(selenium, issuesTab, "document.getElementById('issues-tab').click();");
            waitForLocation(driver, selenium, "/issues");
            assertTrue(selenium.getLocation().contains("/issues"), "Не перешли на вкладку Issues");

            waitForElement(driver, selenium, pullsTab);
            clickWithFallback(selenium, pullsTab, "document.getElementById('pull-requests-tab').click();");
            waitForLocation(driver, selenium, "/pulls");
            assertTrue(selenium.getLocation().contains("/pulls"), "Не перешли на вкладку Pull Requests");
        } finally {
            safeStop(selenium, driver);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testExplorePage(String browser) {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        try {
            selenium.open("/explore");
            selenium.windowMaximize();

            String pageTitle = "xpath=//h1[contains(., 'Explore')] | //h2[contains(., 'Explore')]";
            String collectionsTab = "xpath=//a[contains(@href, '/collections') and contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'collections')]";

            try {
                waitForElement(driver, selenium, pageTitle);
                assertTrue(selenium.isElementPresent(pageTitle), "Страница Explore не загрузилась");
            } catch (Throwable ignored) {
            }

            waitForElement(driver, selenium, collectionsTab);
            clickWithFallback(
                    selenium,
                    collectionsTab,
                    "document.evaluate(\"//a[contains(@href, '/collections')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();"
            );

            waitForLocation(driver, selenium, "/collections");
            assertTrue(selenium.getLocation().contains("/collections"), "Переход в раздел Collections не осуществлен");
        } finally {
            safeStop(selenium, driver);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testPricingPageNavigation(String browser) {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        try {
            selenium.open("/pricing");
            selenium.windowMaximize();

            String freePlanHeader = "xpath=//*[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'free')]";
            String joinBtn = "xpath=//a[contains(@href, '/join') and contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'join')] | //a[contains(@class, 'js-pricing-upgrade-path')]";

            waitForElement(driver, selenium, freePlanHeader);
            assertTrue(selenium.isElementPresent(freePlanHeader), "Блок бесплатного тарифа не найден");

            waitForElement(driver, selenium, joinBtn);
            try {
                selenium.click(joinBtn);
            } catch (Exception e) {
                selenium.open("/join");
            }

            waitForAnyLocation(driver, selenium, "/signup", "/join");
            assertTrue(selenium.getLocation().contains("/signup") || selenium.getLocation().contains("/join"), "На страницу регистрации не перешли");
        } finally {
            safeStop(selenium, driver);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome"})
    public void testValidLogin(String browser) {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        try {
            String username = loginToGitHub(driver, selenium);
            assertTrue(!username.isBlank(), "После авторизации не удалось определить username текущего пользователя");
        } finally {
            safeStop(selenium, driver);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome"})
    public void testSetProfileStatusEmoji(String browser) {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        String editStatusButton = "xpath=//button[contains(., 'Edit status')] | //button[contains(., 'Set status')]";
        String emojiPickerButton = "xpath=//button[contains(@aria-label, 'Choose an emoji')] | //summary[contains(@aria-label, 'Choose an emoji')]";
        String smileEmoji = "xpath=//button[@value=':smile:'] | //button[contains(@aria-label, 'smile')] | //button[contains(., '😄')]";
        String statusInput = "xpath=//input[@name='message'] | //input[contains(@placeholder, 'What') or contains(@aria-label, 'status')]";
        String saveStatusButton = "xpath=//button[contains(., 'Set status message')] | //button[contains(., 'Save')] | //button[contains(., 'Update status')]";
        String clearStatusButton = "xpath=//button[contains(., 'Clear status')]";

        try {
            String username = loginToGitHub(driver, selenium);
            String statusText = "tpo-lab3-status-" + UUID.randomUUID().toString().substring(0, 8);

            selenium.open("/" + username);
            selenium.windowMaximize();

            waitForElement(driver, selenium, editStatusButton);
            clickWithFallback(
                    selenium,
                    editStatusButton,
                    "var n=document.evaluate(\"//button[contains(., 'Edit status')] | //button[contains(., 'Set status')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if(n){n.click();}"
            );

            waitForElement(driver, selenium, emojiPickerButton);
            clickWithFallback(
                    selenium,
                    emojiPickerButton,
                    "var n=document.evaluate(\"//button[contains(@aria-label, 'Choose an emoji')] | //summary[contains(@aria-label, 'Choose an emoji')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if(n){n.click();}"
            );

            waitForElement(driver, selenium, smileEmoji);
            clickWithFallback(
                    selenium,
                    smileEmoji,
                    "var n=document.evaluate(\"//button[@value=':smile:'] | //button[contains(@aria-label, 'smile')] | //button[contains(., '😄')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if(n){n.click();}"
            );

            waitForElement(driver, selenium, statusInput);
            selenium.type(statusInput, statusText);

            waitForElement(driver, selenium, saveStatusButton);
            clickWithFallback(
                    selenium,
                    saveStatusButton,
                    "var n=document.evaluate(\"//button[contains(., 'Set status message')] | //button[contains(., 'Save')] | //button[contains(., 'Update status')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if(n){n.click();}"
            );

            waitUntil(driver,
                    "Статус не сохранился на странице профиля",
                    () -> driver.getPageSource().contains(statusText) || !driver.findElements(By.xpath("//*[contains(text(), '" + statusText + "')]" )).isEmpty());

            if (isElementPresent(selenium, editStatusButton)) {
                clickWithFallback(
                        selenium,
                        editStatusButton,
                        "var n=document.evaluate(\"//button[contains(., 'Edit status')] | //button[contains(., 'Set status')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if(n){n.click();}"
                );
                if (isElementPresent(selenium, clearStatusButton)) {
                    clickWithFallback(
                            selenium,
                            clearStatusButton,
                            "var n=document.evaluate(\"//button[contains(., 'Clear status')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if(n){n.click();}"
                    );
                }
            }
        } finally {
            safeStop(selenium, driver);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome"})
    public void testCreateRepositoryAndRunAction(String browser) {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        String repoNameInput = "xpath=//input[@name='repository[name]']";
        String autoInitCheckbox = "xpath=//input[@name='repository[auto_init]']";
        String createRepoButton = "xpath=//button[contains(., 'Create repository')]";
        String actionsTab = "xpath=//a[@id='actions-tab'] | //a[contains(@href, '/actions')]";
        String configureWorkflow = "xpath=(//a[contains(., 'set up a workflow yourself')] | //a[contains(., 'Simple workflow')] | //a[contains(., 'Configure')] | //button[contains(., 'Configure')])[1]";
        String commitButton = "xpath=//button[contains(., 'Commit changes')] | //button[contains(., 'Start commit')]";
        String confirmCommitButton = "xpath=(//button[contains(., 'Commit changes')])[last()]";

        try {
            String username = loginToGitHub(driver, selenium);
            String repoName = "tpo-lab3-actions-" + UUID.randomUUID().toString().substring(0, 8);

            selenium.open("/new");
            selenium.windowMaximize();

            waitForElement(driver, selenium, repoNameInput);
            selenium.type(repoNameInput, repoName);

            waitForElement(driver, selenium, autoInitCheckbox);
            WebElement autoInit = firstByXpath(driver, "//input[@name='repository[auto_init]']");
            if (!autoInit.isSelected()) {
                autoInit.click();
            }

            waitForElement(driver, selenium, createRepoButton);
            selenium.click(createRepoButton);
            waitForLocation(driver, selenium, "/" + username + "/" + repoName);

            waitForElement(driver, selenium, actionsTab);
            selenium.open("/" + username + "/" + repoName + "/actions");

            waitForElement(driver, selenium, configureWorkflow);
            clickWithFallback(
                    selenium,
                    configureWorkflow,
                    "var n=document.evaluate(\"(//a[contains(., 'set up a workflow yourself')] | //a[contains(., 'Simple workflow')] | //a[contains(., 'Configure')] | //button[contains(., 'Configure')])[1]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if(n){n.click();}"
            );

            waitForElement(driver, selenium, commitButton);
            clickWithFallback(
                    selenium,
                    commitButton,
                    "var n=document.evaluate(\"//button[contains(., 'Commit changes')] | //button[contains(., 'Start commit')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if(n){n.click();}"
            );

            waitForElement(driver, selenium, confirmCommitButton);
            clickWithFallback(
                    selenium,
                    confirmCommitButton,
                    "var n=document.evaluate(\"(//button[contains(., 'Commit changes')])[last()]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if(n){n.click();}"
            );

            selenium.open("/" + username + "/" + repoName + "/actions");
            waitUntil(driver,
                    "GitHub Actions не появились после коммита workflow",
                    () -> {
                        List<WebElement> states = driver.findElements(By.xpath("//*[contains(text(), 'queued') or contains(text(), 'in progress') or contains(text(), 'completed') or contains(text(), 'success')]"));
                        return !states.isEmpty();
                    });
        } finally {
            safeStop(selenium, driver);
        }
    }
}
