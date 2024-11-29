import config.Config;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import server.DummyServer;

import java.io.IOException;

public class Success {
    public static void main(String[] args) throws TelegramApiException, IOException {
        Config.readData();
        System.out.println("I'll do it.\nNo doubts.");
        DummyServer.startServer();
        TelegramBot telegramBot = new TelegramBot(); //создание инстанса бота
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class); //создание API
        telegramBotsApi.registerBot(telegramBot); //регистрация бота в API
    }
}