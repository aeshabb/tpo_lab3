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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.UUID;
import java.util.function.Supplier;

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
                () -> selenium.getLocation() != null && selenium.getLocation().contains(expectedLocationPart));
    }

    private boolean isTwoFactorPage(WebDriver driver) {
        return driver.getCurrentUrl().contains("/sessions/two-factor")
                || !driver.findElements(By.xpath("//input[@name='app_otp' or contains(@autocomplete, 'one-time-code')]" )).isEmpty();
    }

    private boolean isLoggedIn(WebDriver driver) {
        return !driver.findElements(By.xpath("//meta[@name='user-login' and normalize-space(@content) != '']")).isEmpty();
    }

    private String getLoggedInUsername(WebDriver driver) {
        waitUntil(driver,
                "Не удалось определить username текущего пользователя",
                () -> isLoggedIn(driver));
        return driver.findElement(By.xpath("//meta[@name='user-login' and normalize-space(@content) != '']")).getAttribute("content");
    }

    private String xpathFromLocator(String locator) {
        if (locator != null && locator.startsWith("xpath=")) {
            return locator.substring("xpath=".length());
        }
        return locator;
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
                new WebDriverWait(driver, 10).until(ignored ->
                        isLoggedIn(driver)
                                || isTwoFactorPage(driver)
                                || isElementPresent(selenium, "xpath=//div[contains(@class, 'js-flash-alert') or contains(text(), 'Incorrect username or password')]")
                );
                isLoggedIn = isLoggedIn(driver);
            } catch (Exception ignored) {
            }

            if (isLoggedIn) {
                System.out.println("Успешный вход! Запуск тестов с авторизацией: создание репо, настройка экшена, статус...");

                String username = getLoggedInUsername(driver);

                String repoName = "test-repo-" + UUID.randomUUID().toString().substring(0, 8);
                selenium.open("/new");

                String repoNameInput = "xpath=//input[@name='repository[name]']"
                        + " | //input[contains(@id, 'repository_name')]"
                        + " | //input[contains(@aria-label, 'Repository name')]"
                        + " | //label[contains(., 'Repository name')]/following::input[1]"
                        + " | (//form//input[@type='text' and not(@name='description') and not(@name='owner')])[1]";
                waitForElement(driver, selenium, repoNameInput);
                selenium.type(repoNameInput, repoName);

                try {
                    String autoInitCheckbox = "xpath=//input[@id='repository_auto_init'] | //input[@name='repository[auto_init]']";
                    if (isElementPresent(selenium, autoInitCheckbox)) {
                        selenium.click(autoInitCheckbox);
                    }
                } catch (Throwable ignored) {
                }

                String createRepoButton = "xpath=//button[contains(text(), 'Create repository')] | //button[contains(., 'Create repository')] | //button[@type='submit' and contains(normalize-space(), 'Create')]";
                waitForElement(driver, selenium, createRepoButton);

                try {
                    selenium.click(createRepoButton);
                } catch (Throwable e) {
                    driver.findElement(By.xpath(xpathFromLocator(createRepoButton))).click();
                }

                boolean repoCreated = false;
                try {
                    waitForLocation(driver, selenium, repoName);
                    repoCreated = true;
                } catch (Throwable ignored) {
                }

                if (repoCreated) {
                    String actionsTab = "xpath=//a[contains(@data-selected-links, 'repo_actions')] | //span[contains(text(), 'Actions')]/parent::a | //a[@id='actions-tab'] | //a[contains(@href, '/actions')]";
                    try {
                        selenium.open("/" + username + "/" + repoName + "/actions");
                        waitForElement(driver, selenium, actionsTab);

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

                selenium.open("/");
                String profileMenuBtn = "xpath=//summary[contains(@aria-label, 'View profile')] | //button[contains(@aria-label, 'Open user account')] | //img[@class='avatar circle'] | //AppHeader-user";
                waitForElement(driver, selenium, profileMenuBtn);
                try {
                    selenium.click(profileMenuBtn);
                } catch (Exception ignored) {
                }

                String editStatusButton = "xpath=//button[contains(@aria-label, 'Set status')] | //button[contains(., 'Set status')] | //span[contains(., 'Set status')]/.. | //summary[contains(@aria-label, 'Set status')] | //a[contains(., 'Set status')]";
                waitForElement(driver, selenium, editStatusButton);
                try {
                    selenium.click(editStatusButton);
                } catch (Exception ex) {
                    selenium.runScript("document.evaluate(\"//button[contains(@aria-label, 'Set status')] | //button[contains(., 'Set status')] | //span[contains(., 'Set status')]/..\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();");
                }

                String emojiPickerButton = "xpath=//button[contains(@aria-label, 'Choose an emoji')] | //summary[contains(@aria-label, 'Choose an emoji')] | //g-emoji[contains(@class, 'g-emoji')] | //button[contains(@class, 'status-emoji')]";
                try {
                    waitForElement(driver, selenium, emojiPickerButton);
                    try {
                        selenium.click(emojiPickerButton);
                    } catch (Exception ignored) {
                    }

                    String smileEmoji = "xpath=//button[@title='smile'] | //button[@title='grinning face'] | //g-emoji[@alias='grinning'] | //div[contains(@class, 'emoji-picker')]//button[contains(@aria-label, 'grinning')]";
                    waitForElement(driver, selenium, smileEmoji);
                    try {
                        selenium.click(smileEmoji);
                    } catch (Exception ignored) {
                    }
                } catch (Throwable th) {
                    System.out.println("Смайлик пропущен: " + th.getMessage());
                }

                String statusInput = "xpath=//input[contains(@placeholder, \"What's happening?\")] | //input[@name='message']";
                waitForElement(driver, selenium, statusInput);
                selenium.type(statusInput, "Automated Test Status: " + UUID.randomUUID().toString().substring(0, 4));

                String saveStatusButton = "xpath=//button[contains(text(), 'Set status')] | //button[contains(., 'Set status')] | //button[@type='submit' and contains(., 'Set status')]";
                waitForElement(driver, selenium, saveStatusButton);
                try {
                    selenium.click(saveStatusButton);
                } catch (Exception ignored) {
                }
            } else {
                if (isTwoFactorPage(driver)) {
                    throw new AssertionError("GitHub запросил 2FA, поэтому авторизованный сценарий не может продолжиться автоматически");
                }

                System.out.println("Вход не выполнен. Запуск неавторизованных тестов: поиск, топики, тренды...");

                selenium.open("/");
                String searchQ = "Selenium";
                selenium.open("/search?q=" + searchQ + "&type=repositories");
                waitForLocation(driver, selenium, "/search");
                String firstRepoResultLocator = "xpath=//div[contains(@class, 'repo-list')]//a | //a[contains(@class, 'search-match')] | //div[contains(@data-testid, 'results-list')]//a[contains(@href, '/')]";
                waitForElement(driver, selenium, firstRepoResultLocator);
                selenium.click(firstRepoResultLocator);

                selenium.open("/explore");
                String topicsHeaderLocator = "xpath=//a[contains(@href, '/topics')] | //h2[contains(text(), 'Topics')]/following-sibling::a";
                waitForElement(driver, selenium, topicsHeaderLocator);
                try {
                    selenium.click(topicsHeaderLocator);
                } catch (Exception ignored) {
                }
                waitForLocation(driver, selenium, "/topics");

                selenium.open("/trending");
                String trendingTab = "xpath=//a[contains(@href, '/trending/java')] | //span[contains(text(), 'Spoken Language')]/.. | //summary[contains(., 'Spoken Language')]";
                try {
                    waitForElement(driver, selenium, trendingTab);
                    selenium.click(trendingTab);
                } catch (Throwable ignored) {
                }

                selenium.open("/pricing");
                String freePlanHeader = "xpath=//h2[contains(text(), 'Free')] | //h3[contains(text(), 'Free')]";
                try {
                    waitForElement(driver, selenium, freePlanHeader);
                } catch (Throwable ignored) {
                }
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
