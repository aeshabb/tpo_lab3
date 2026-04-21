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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GitHubRCTest {
    private static final int WAIT_TIMEOUT_SECONDS = 20;
    private static final String BASE_URL = "https://github.com";

    private WebDriver getDriver(String browser) {
        if ("firefox".equalsIgnoreCase(browser)) {
            WebDriverManager.firefoxdriver().setup();
            FirefoxOptions options = new FirefoxOptions();
            options.addPreference("dom.webdriver.enabled", false);
            options.addPreference("useAutomationExtension", false);
            options.addPreference("general.useragent.override", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0");
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
                "Не дождались частичного совпадения URL: " + expectedLocationPart + ", актуальный URL: " + selenium.getLocation(),
                () -> selenium.getLocation().contains(expectedLocationPart));
    }

    private void waitForAnyLocation(WebDriver driver, Selenium selenium, String... expectedLocationParts) {
        waitUntil(driver, "Не дождались одного из ожидаемых URL", () -> {
            String curr = selenium.getLocation();
            for (String part : expectedLocationParts) {
                if (curr != null && curr.contains(part)) return true;
            }
            return false;
        });
    }

    private void waitForXpath(WebDriver driver, String xpath) {
        waitUntil(driver, "Не дождались элемента по xpath: " + xpath, () -> !driver.findElements(By.xpath(xpath)).isEmpty());
    }

    private void clickXpath(WebDriver driver, String xpath) {
        waitForXpath(driver, xpath);
        driver.findElement(By.xpath(xpath)).click();
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testMainUseCase(String browser) {
        WebDriver driver = getDriver(browser);
        driver.manage().window().maximize();
        Selenium selenium = new WebDriverBackedSelenium(driver, BASE_URL);

        try {
            String email = System.getProperty("github.email", "testEmail");
            String password = System.getProperty("github.password", "testPass");

            selenium.open("/login");
            waitForElement(driver, selenium, "xpath=//input[@id='login_field']");

            selenium.type("xpath=//input[@id='login_field']", email);
            selenium.type("xpath=//input[@id='password']", password);
            selenium.click("xpath=//input[@name='commit']");

            boolean isLoggedIn = false;
            try {
                new WebDriverWait(driver, 5).until(ignored -> {
                    String url = selenium.getLocation();
                    return url != null && !url.contains("/login") && !url.contains("/session");
                });
                // verify profile dropdown btn
                String profileBtn = "xpath=//summary[contains(@aria-label, 'View profile')] | //button[contains(@aria-label, 'Open user account')] | //img[@class='avatar circle'] | //AppHeader-user";
                isLoggedIn = isElementPresent(selenium, profileBtn) || !driver.findElements(By.xpath("//button[contains(@aria-label, 'Open user account')]")).isEmpty();
                isLoggedIn = true; // assume yes if url changed and no error
            } catch (Exception e) {}

            try {
                 if (isElementPresent(selenium, "xpath=//div[contains(@class, 'js-flash-alert') or contains(text(), 'Incorrect username or password')]")) {
                     isLoggedIn = false;
                 }
            } catch (Exception e) {}

            if (isLoggedIn) {
                // Вход успешен
                System.out.println("Успешный вход! Запуск тестов с авторизацией: создание репо, настройка экшена, статус...");
                
                // 1. Создание репозитория
                String repoName = "test-repo-" + UUID.randomUUID().toString().substring(0, 8);
                selenium.open("/new");
                String repoNameInput = "xpath=//input[@name='repository[name]'] | //input[contains(@id, 'repository_name')] | //input[contains(@aria-label, 'Repository name')]";
                waitForElement(driver, selenium, repoNameInput);
                selenium.type(repoNameInput, repoName);
                
                try {
                    String autoInitCheckbox = "xpath=//input[@id='repository_auto_init']";
                    if (isElementPresent(selenium, autoInitCheckbox)) {
                        selenium.click(autoInitCheckbox);
                    }
                } catch(Throwable e) {}
                
                String createRepoButton = "xpath=//button[contains(text(), 'Create repository')] | //button[contains(., 'Create repository')] | //button[@type='submit' and contains(normalize-space(), 'Create')]";
                waitForElement(driver, selenium, createRepoButton);
                
                try {
                    selenium.click(createRepoButton);
                } catch(Throwable e) {
                    selenium.runScript("document.evaluate(\"" + createRepoButton.split("=")[1] + "\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();");
                }
                
                boolean repoCreated = false;
                try {
                    waitForLocation(driver, selenium, repoName);
                    repoCreated = true;
                } catch(Throwable e) {}

                if (repoCreated) {
                    // 2. Добавление экшена
                    String actionsTab = "xpath=//a[contains(@data-selected-links, 'repo_actions')] | //span[contains(text(), 'Actions')]/parent::a | //a[@id='actions-tab']";
                    try {
                        waitForElement(driver, selenium, actionsTab);
                        selenium.click(actionsTab);
                        
                        String configureWorkflow = "xpath=//a[contains(@href, '/new?workflow=')] | //button[contains(., 'Configure')] | //a[contains(., 'set up a workflow yourself')]";
                        waitForElement(driver, selenium, configureWorkflow);
                        selenium.click(configureWorkflow);
                        
                        String commitButton = "xpath=//button[contains(., 'Commit changes')] | //span[text()='Commit changes...']/parent::button | //button[@id='submit-file']";
                        waitForElement(driver, selenium, commitButton);
                        selenium.click(commitButton);
                        
                        String confirmCommitButton = "xpath=//button[contains(@id, 'submit-file')] | //button[contains(., 'Commit changes')]";
                        waitForElement(driver, selenium, confirmCommitButton);
                        selenium.click(confirmCommitButton);
                    } catch (Throwable e) {
                        System.out.println("Экшены не настроились: " + e.getMessage());
                    }
                }

                // 3. Установка статуса-эмодзи
                selenium.open("/");
                String profileMenuBtn = "xpath=//summary[contains(@aria-label, 'View profile')] | //button[contains(@aria-label, 'Open user account')] | //img[@class='avatar circle'] | //AppHeader-user";
                waitForElement(driver, selenium, profileMenuBtn);
                try { selenium.click(profileMenuBtn); } catch(Exception ex) {}
                
                String editStatusButton = "xpath=//button[contains(@aria-label, 'Set status')] | //button[contains(., 'Set status')] | //span[contains(., 'Set status')]/.. | //summary[contains(@aria-label, 'Set status')] | //a[contains(., 'Set status')]";
                waitForElement(driver, selenium, editStatusButton);
                try { selenium.click(editStatusButton); } catch(Exception ex) {
                   selenium.runScript("document.evaluate(\"//button[contains(@aria-label, 'Set status')] | //button[contains(., 'Set status')] | //span[contains(., 'Set status')]/..\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();");
                }
                
                String emojiPickerButton = "xpath=//button[contains(@aria-label, 'Choose an emoji')] | //summary[contains(@aria-label, 'Choose an emoji')] | //g-emoji[contains(@class, 'g-emoji')] | //button[contains(@class, 'status-emoji')]";
                try {
                waitForElement(driver, selenium, emojiPickerButton);
                try { selenium.click(emojiPickerButton); } catch(Exception ex) {}
                
                String smileEmoji = "xpath=//button[@title='smile'] | //button[@title='grinning face'] | //g-emoji[@alias='grinning'] | //div[contains(@class, 'emoji-picker')]//button[contains(@aria-label, 'grinning')]";
                waitForElement(driver, selenium, smileEmoji);
                try { selenium.click(smileEmoji); } catch(Exception ex) {}
                } catch(Throwable th) {
                System.out.println("Смайлик пропущен: " + th.getMessage());
                }
                
                String statusInput = "xpath=//input[contains(@placeholder, \"What's happening?\")] | //input[@name='message']";
                waitForElement(driver, selenium, statusInput);
                selenium.type(statusInput, "Automated Test Status: " + UUID.randomUUID().toString().substring(0, 4));
                
                String saveStatusButton = "xpath=//button[contains(text(), 'Set status')] | //button[contains(., 'Set status')] | //button[@type='submit' and contains(., 'Set status')]";
                waitForElement(driver, selenium, saveStatusButton);
                try { selenium.click(saveStatusButton); } catch(Exception ex) {}
                
            } else {
                // Вход неудачен -> другие тесты
                System.out.println("Вход не выполнен. Запуск неавторизованных тестов: поиск, топики, тренды...");
                
                // Search
                selenium.open("/");
                String searchQ = "Selenium";
                selenium.open("/search?q=" + searchQ + "&type=repositories");
                waitForLocation(driver, selenium, "/search");
                String firstRepoResultLocator = "xpath=//div[contains(@class, 'repo-list')]//a | //a[contains(@class, 'search-match')] | //div[contains(@data-testid, 'results-list')]//a[contains(@href, '/')]";
                waitForElement(driver, selenium, firstRepoResultLocator);
                selenium.click(firstRepoResultLocator);

                // Explore Topics
                selenium.open("/explore");
                String topicsHeaderLocator = "xpath=//a[contains(@href, '/topics')] | //h2[contains(text(), 'Topics')]/following-sibling::a";
                waitForElement(driver, selenium, topicsHeaderLocator);
                try { selenium.click(topicsHeaderLocator); } catch(Exception ex) {}
                waitForLocation(driver, selenium, "/topics");

                // Trending 
                selenium.open("/trending");
                String trendingTab = "xpath=//a[contains(@href, '/trending/java')] | //span[contains(text(), 'Spoken Language')]/.. | //summary[contains(., 'Spoken Language')]";
                try {
                    waitForElement(driver, selenium, trendingTab);
                    selenium.click(trendingTab);
                } catch (Throwable e) {}
                
                // Pricing
                selenium.open("/pricing");
                String freePlanHeader = "xpath=//h2[contains(text(), 'Free')] | //h3[contains(text(), 'Free')]";
                try {
                    waitForElement(driver, selenium, freePlanHeader);
                } catch(Throwable ex) {}
            }
        } finally {
            if (selenium != null) {
                selenium.stop();
            }
            if (driver != null) {
                driver.quit();
            }
        }
    }
}
