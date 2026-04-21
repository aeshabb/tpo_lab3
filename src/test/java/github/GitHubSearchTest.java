package github;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тестирование GitHub на основе прецедентов использования.
 * <p>
 * Прецеденты:
 * 1. Поиск репозитория (Search Repository).
 * 2. Переход в репозиторий из списка результатов и проверка названия.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GitHubSearchTest {

    private WebDriver getDriver(String browser) {
        if ("firefox".equalsIgnoreCase(browser)) {
            FirefoxOptions options = new FirefoxOptions();
            return new FirefoxDriver(options);
        } else {
            ChromeOptions options = new ChromeOptions();
            return new ChromeDriver(options);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testSearchRepository(String browser) {
        WebDriver driver = getDriver(browser);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // Прецедент 1: Открытие главной страницы
            driver.get("https://github.com/");
            driver.manage().window().maximize();

            // Прецедент 2: Вызов диалогового окна поиска
            // Используем XPath для обхода динамических ID
            WebElement searchButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(@class, 'header-search-button') or contains(@aria-label, 'Search')]")
            ));
            searchButton.click();

            // Прецедент 3: Ввод текста в модальном окне поиска
            WebElement searchInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//input[contains(@name, 'query-builder-test') or contains(@placeholder, 'Search')]")
            ));
            searchInput.sendKeys("Selenium WebDriver");
            searchInput.sendKeys(Keys.ENTER);

            // Прецедент 4: Проверка, что результаты отображены
            WebElement resultsArea = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[contains(@data-testid, 'results-list')]")
            ));
            assertTrue(resultsArea.isDisplayed(), "Список результатов поиска не отображен.");

            // Прецедент 5: Клик по первому репозиторию в списке
            WebElement firstRepoLink = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("(//div[contains(@data-testid, 'results-list')]//a[contains(@href, '/') and not(contains(@href, '/search'))])[1]")
            ));
            String expectedUrlPath = firstRepoLink.getAttribute("href");
            firstRepoLink.click();

            // Прецедент 6: Проверка, что мы зашли в правильный репозиторий
            wait.until(ExpectedConditions.urlToBe(expectedUrlPath));
            assertTrue(driver.getCurrentUrl().startsWith(expectedUrlPath), "URL репозитория не совпадает.");

        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }
}