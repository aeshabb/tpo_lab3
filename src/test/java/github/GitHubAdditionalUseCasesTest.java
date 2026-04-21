package github;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.By;
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
 * Дополнительные прецеденты использования GitHub.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GitHubAdditionalUseCasesTest {

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
    public void testCheckTopicsPage(String browser) {
        WebDriver driver = getDriver(browser);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // Прецедент: Переход на страницу Topics и просмотр тем
            // 1. Открываем страницу топиков
            driver.get("https://github.com/topics");
            driver.manage().window().maximize();

            // 2. Проверяем заголовок
            WebElement topicsHeader = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//h1[contains(text(), 'Topics')]")
            ));
            assertTrue(topicsHeader.isDisplayed(), "Заголовок Topics не найден");

            // 3. Выбираем первую карточку темы и кликаем по ней
            WebElement firstTopic = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("(//a[starts-with(@href, '/topics/') and contains(@class, 'no-underline')])[1]")
            ));
            String expectedTopicUrl = firstTopic.getAttribute("href");
            firstTopic.click();

            // 4. Проверяем, что загрузилась нужная страница
            wait.until(ExpectedConditions.urlContains(expectedTopicUrl));
            assertTrue(driver.getCurrentUrl().startsWith(expectedTopicUrl), "Переход к теме не удался");

        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testTrendingRepositories(String browser) {
        WebDriver driver = getDriver(browser);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // Прецедент: Просмотр списка популярных (Trending) репозиториев
            // 1. Открываем страницу трендов
            driver.get("https://github.com/trending");
            driver.manage().window().maximize();

            // 2. Проверяем наличие заголовка "Trending"
            WebElement trendingHeader = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//h1[contains(text(), 'Trending')]")
            ));
            assertTrue(trendingHeader.isDisplayed(), "Заголовок Trending не найден на странице");

            // 3. Проверяем, что в списке отображается хотя бы один репозиторий (статья с классом Box-row)
            WebElement firstTrendingRepo = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("(//article[contains(@class, 'Box-row')])[1]")
            ));
            assertTrue(firstTrendingRepo.isDisplayed(), "Список популярных репозиториев не загрузился или пуст");

        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"chrome", "firefox"})
    public void testUserProfileAndRepositories(String browser) {
        WebDriver driver = getDriver(browser);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // Прецедент: Просмотр профиля пользователя и переход к списку его репозиториев
            // 1. Открываем профиль Линуса Торвальдса (создателя Linux/Git)
            driver.get("https://github.com/torvalds");
            driver.manage().window().maximize();

            // 2. Проверяем, что имя пользователя отображается корректно
            WebElement userName = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//span[contains(@class, 'vcard-fullname') and contains(text(), 'Linus Torvalds')]")
            ));
            assertTrue(userName.isDisplayed(), "Имя 'Linus Torvalds' не найдено на странице профиля");

            // 3. Выбираем вкладку Repositories и кликаем по ней
            WebElement reposTab = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[contains(@data-tab-item, 'repositories') or span[contains(text(), 'Repositories')]]")
            ));
            reposTab.click();

            // 4. Проверяем, что загрузился список репозиториев (по наличию элементов li внутри списка)
            WebElement firstRepoInList = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("(//div[@id='user-repositories-list']//li)[1]")
            ));
            assertTrue(firstRepoInList.isDisplayed(), "Список репозиториев не загрузился или пуст");

        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }
}