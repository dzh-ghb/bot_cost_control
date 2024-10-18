import config.Config;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TelegramBot extends TelegramLongPollingBot { //наследование от класса, позволяющего взаимодействовать с API Telegram'а
    private static final String USERNAME = Config.getUsername(); //название бота
    private static final String TOKEN = Config.getToken(); //токен бота

    private static final String ADD_EXPENSE_BTN = "Добавить расход";
    private static final String SHOW_CATEGORIES_BTN = "Список категорий";
    private static final String SHOW_EXPENSES_BTN = "Список расходов";

    private static final Map<String, List<Integer>> EXPENSES = new TreeMap<>(); //хранению категории (ключа) и трат в этой категории (значение)

    //нужно вынести токен и название в отдельный файл, подтягивать через InputStream
    //название бота
    @Override
    public String getBotUsername() {
        return USERNAME;
    }

    //ключ доступа (токен) к боту
    @Override
    public String getBotToken() {
        return TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        /*если нет сообщения ИЛИ нет текста в сообщении - выход из метода;
        GUARD EXPRESSION - подход заключается в проверке негативных кейсов,
        если срабатывает условие, которое нас не устраивает, то, например,
        программа завершается/происходит выход из цикла;
        такой подход позволяет избежать большой вложенности условий
        (можно было сделать if с проверкой не инвертированных условий - если есть сообщение и текст в нем*/
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            System.out.println("Unsupported update - no message/no text in message".concat("\n------------------------"));
            return;
        }
        Message message = update.getMessage();

        Long chatId = message.getChatId();
        String messageText = message.getText();
        String userName = message.getFrom().getUserName();

        String logMessage = "From: ".concat(userName).concat("\nMessage: \"").concat(messageText).concat("\"");
        System.out.println(logMessage.concat("\n------------------------"));

        switch (messageText) {
            case "/start":
                sendKeyboard(chatId, String.format("Привет, %s!\nЯ помогу тебе контролировать твои расходы!\nВнесите первую запись в формате: \"Категория Сумма\"", userName), replyKeyboardMarkup());
                break;
            case ADD_EXPENSE_BTN:
                sendKeyboard(chatId, "Введите название категории и сумму трат через пробел", replyKeyboardMarkup());
                break;
            case SHOW_CATEGORIES_BTN:
                sendKeyboard(chatId, getFormattedCategories(), replyKeyboardMarkup());
                break;
            case SHOW_EXPENSES_BTN:
                sendKeyboard(chatId, getFormattedExpenses(), replyKeyboardMarkup());
                break;
            default: { //обработка текста, не соответствующего кнопкам
                List<String> splitMessage = List.of(messageText.split("\\s+")); //разделение текста по пробелам
                if (splitMessage.size() == 2) { //если формат сообщения верный: "Категория Сумма"
                    StringBuilder formattedCategory = new StringBuilder(); //для формирования название категории в первой заглавное и остальными строчными буквами
                    String firstLetter = splitMessage.get(0).substring(0, 1).toUpperCase(); //первая заглавная буква
                    String otherLetters = splitMessage.get(0).substring(1).toLowerCase(); //остальные буквы - строчные
                    String category = formattedCategory.append(firstLetter).append(otherLetters).toString();
//                    if (!EXPENSES.containsKey(category)) { //если такой категории еще не было, то добавляем ее
//                        EXPENSES.put(category, new ArrayList<>()); /*добавление категории (ключ) и создание пустого списка трат,
//                        который будет дополняться впоследствии (значение); пустой список необходим для того, чтобы последующие
//                        записи трат происходили без проблем (на момент добавления траты список уже будет)*/
//                    }
                    EXPENSES.putIfAbsent(category, new ArrayList<>()); //аналог закомментированного выше кода - добавление только, если записей с таким ключом еще не было
                    Integer amount = Integer.parseInt(splitMessage.get(1)); //сумма трат
                    EXPENSES.get(category).add(amount); //добавление траты по категории
                } else {
                    sendKeyboard(chatId, String.format("\"%s\"?\nНекорректное значение\nФормат ввода: \"Категория Сумма\"", messageText), replyKeyboardMarkup());
                }
                break;
            }
        }
    }

    //отправка текстового сообщения
    private void sendMessage(Long chatId, String messageText) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(messageText)
                .build();
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            System.out.println(e.getMessage());
        }
    }

    //отправка текстового сообщения и клавиатуры
    private void sendKeyboard(Long chatId, String messageText, ReplyKeyboardMarkup replyKeyboardMarkup) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(messageText)
                .replyMarkup(replyKeyboardMarkup)
                .build();
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            System.out.println(e.getMessage());
        }
    }

    //клавиатура для ответа, заменяющая основную (нажатие кнопки отправляет текст с кнопки)
    private static ReplyKeyboardMarkup replyKeyboardMarkup() {
        KeyboardRow firstRow = new KeyboardRow(); //первый ряд кнопок
        List<String> buttonsNames = List.of(ADD_EXPENSE_BTN, SHOW_CATEGORIES_BTN);
        firstRow.addAll(buttonsNames);

        KeyboardRow secondRow = new KeyboardRow(); //второй ряд кнопок
        buttonsNames = List.of(SHOW_EXPENSES_BTN);
        secondRow.addAll(buttonsNames);

        List<KeyboardRow> rows = List.of(firstRow, secondRow); //список рядов кнопок

        return ReplyKeyboardMarkup.builder() //создание клавиатуры
                .keyboard(rows) //кнопки клавиатуры (добавление списка рядов кнопок)
                .resizeKeyboard(true) //приведение размера клавиш к нормальному
                .build();
    }

    private String getFormattedCategories() {
        return String.join("\n", EXPENSES.keySet());
    }

    private String getFormattedExpenses() {
        StringBuilder formattedResult = new StringBuilder();
        for (Map.Entry<String, List<Integer>> category : EXPENSES.entrySet()) { //перебор всех записей в списке трат, берется категория
            StringBuilder categoryExpenses = new StringBuilder();
            for (Integer expense : category.getValue()) { //для каждой категории перебираем все траты
                categoryExpenses.append(expense).append(", "); //собираем все траты в строку
            }
            formattedResult.append(category.getKey()).append(": ").append(categoryExpenses.substring(0, categoryExpenses.length() - 2)).append("\n"); //формирование ответа в виде "Категория - 100 200 300 400"
        }
        return formattedResult.toString();
    }
}