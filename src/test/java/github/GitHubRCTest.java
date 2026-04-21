package github;

import com.thoughtworks.selenium.Selenium;
import com.thoughtworks.selenium.webdriven.WebDriverBackedSelenium;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Исполнение шаблонов Selenium IDE при помощи Selenium RC API (com.thoughtworks.selenium).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GitHubRCTest {

    private WebDriver getDriver(String browser) {
        if ("firefox".equalsIgnoreCase(browser)) {
            WebDriverManager.firefoxdriver().setup();
            FirefoxOptions options = new FirefoxOptions();
            options.addPreference("dom.webdriver.enabled", false);
            options.addPreference("useAutomationExtension", false);
            options.addPreference("general.useragent.override", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0");
            options.addPreference("network.http.referer.spoofSource", true);
            return new FirefoxDriver(options);
        } else {
            WebDriverManager.chromedriver().setup();
            return new ChromeDriver();
        }
    }

    private void waitForElement(Selenium selenium, String locator) throws InterruptedException {
        int timeoutSeconds = 15;
        for (int i = 0; i < timeoutSeconds; i++) {
            if (selenium.isElementPresent(locator)) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Элемент не дождался своего появления: " + locator);
    }

    private void safeStop(Selenium selenium, WebDriver driver) {
        try {
            // Пытаемся закрыть через RC API
            selenium.stop();
        } catch (Exception e) {
            // В Firefox современные драйверы иногда кидают ошибку при закрытии через старый RC API
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignore) {}
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testSearchRepository(String browser) throws InterruptedException {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, "https://github.com");

        try {
            // ИСПОЛНЕНИЕ ШАБЛОНА 1: Поиск репозитория
            selenium.open("/");
            selenium.windowMaximize();

            String searchBtnLocator = "xpath=//button[contains(@class, 'header-search-button') or contains(@aria-label, 'Search')]";
            String searchInputLocator = "xpath=//input[contains(@name, 'query-builder-test') or contains(@placeholder, 'Search') or @id='query-builder-test']";
            String searchResultsLocator = "xpath=//div[contains(@data-testid, 'results-list')] | //ul[contains(@class, 'repo-list')]";
            String firstRepoResultLocator = "xpath=(//div[contains(@data-testid, 'results-list')]//a[contains(@href, '/') and not(contains(@href, '/search'))] | //ul[contains(@class, 'repo-list')]//a[contains(@href, '/')])[1]";

            waitForElement(selenium, searchBtnLocator);
            try {
                selenium.click(searchBtnLocator);
            } catch (Exception e) {
                selenium.runScript("document.evaluate(\"//button[contains(@class, 'header-search-button') or contains(@aria-label, 'Search')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();");
            }

            waitForElement(selenium, searchInputLocator);
            selenium.open("/search?q=Selenium+WebDriver&type=repositories");
            Thread.sleep(3000);
            waitForElement(selenium, searchResultsLocator);
            assertTrue(selenium.isElementPresent(searchResultsLocator), "Результаты поиска не появились.");

            waitForElement(selenium, firstRepoResultLocator);
            selenium.click(firstRepoResultLocator);

            Thread.sleep(5000); 
            assertTrue(selenium.getLocation().length() > "https://github.com/".length() && !selenium.getLocation().contains("/search"), "Не перешли на страницу репозитория");
        } finally {
            safeStop(selenium, driver);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testTopics(String browser) throws InterruptedException {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, "https://github.com");

        try {
            // ИСПОЛНЕНИЕ ШАБЛОНА 2: Просмотр Топиков / Трендов
            selenium.open("/topics");
            selenium.windowMaximize();
            
            String topicsHeaderLocator = "xpath=//h1[contains(text(), 'Topics')]";
            String firstTopicLocator = "xpath=(//a[starts-with(@href, '/topics/') and contains(@class, 'no-underline')])[1]";

            waitForElement(selenium, topicsHeaderLocator);
            assertTrue(selenium.isElementPresent(topicsHeaderLocator), "Заголовок Topics не обнаружен");

            waitForElement(selenium, firstTopicLocator);
            selenium.click(firstTopicLocator);

            Thread.sleep(5000);
            assertTrue(selenium.getLocation().contains("/topics/"), "На страницу топика переход не осуществлен");

        } finally {
            safeStop(selenium, driver);
        }
    }


    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testTrendingJavaRepositories(String browser) throws InterruptedException {
        WebDriver driver = getDriver(browser);
        Selenium selenium = new WebDriverBackedSelenium(driver, "https://github.com");

        try {
            // ИСПОЛНЕНИЕ ШАБЛОНА 3: Фильтрация трендовых репозиториев по языку (Java)
            selenium.open("/trending");
            selenium.windowMaximize();
            Thread.sleep(2000);
            
            // Локаторы для выпадающего списка языков и элемента "Java"
            String languageDropdown = "xpath=//summary[contains(., 'Language') or contains(., 'Any language')]";
            String javaOption = "xpath=//a[contains(@href, '/trending/java')]";

            waitForElement(selenium, languageDropdown);
            try {
                selenium.click(languageDropdown);
            } catch (Exception e) {
                selenium.runScript("document.evaluate(\"//summary[contains(., 'Language') or contains(., 'Any language')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();");
            }

            waitForElement(selenium, javaOption);
            try {
                selenium.click(javaOption);
            } catch (Exception e) {
                selenium.runScript("document.evaluate(\"//a[contains(@href, '/trending/java')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.click();");
            }

            Thread.sleep(5000);
            // Проверка, что мы перешли на страницу трендов Java
            assertTrue(selenium.getLocation().contains("/trending/java"), "Переход к трендам Java не осуществлен");

        } finally {
            safeStop(selenium, driver);
        }
    }

}
