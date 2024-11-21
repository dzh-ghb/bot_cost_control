import config.Config;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

//наследование от класса, позволяющего взаимодействовать с API Telegram'а
public class TelegramBot extends TelegramLongPollingBot {
    private static final String USERNAME = Config.getUsername();
    private static final String TOKEN = Config.getToken();

    //варианты переходов состояний:
    //1. IDLE -> AWAITS_CATEGORY -> AWAITS_EXPENSE -> IDLE
    //2. Возможен кольцевой переход: IDLE -> IDLE
    //константы - названия состояний бота
    private static final String IDLE_STATE = "IDLE";
    private static final String AWAITS_CATEGORY_STATE = "AWAITS_CATEGORY";
    private static final String AWAITS_EXPENSE_STATE = "AWAITS_EXPENSE";

    //переменная для хранения текущего состояния бота
    private static String currentState = IDLE_STATE;
    //переменная для хранения введенной в состоянии AWAITS_CATEGORY_STATE категории
    private static String lastCategory = null;

    //константы - названия кнопок
    private static final String ADD_EXPENSE_BTN = "Добавить расход";
    private static final String SHOW_CATEGORIES_BTN = "Список категорий";
    private static final String SHOW_EXPENSES_BTN = "Список расходов";

    //список кнопок для основной клавиатуры
    private static final List<String> MAIN_BTN_LIST = List.of(ADD_EXPENSE_BTN, SHOW_CATEGORIES_BTN, SHOW_EXPENSES_BTN);

    //коллекция для хранения категорий (ключ) и списка трат в этих категориях (значение)
    private static final Map<String, List<Integer>> EXPENSES = new TreeMap<>();

    @Override
    public String getBotUsername() {
        return USERNAME;
    }

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
        (можно было сделать if с проверкой не инвертированных условий -
        если есть сообщение и текст в нем, то выполнить код в теле)*/
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            System.out.println("Unsupported update - no message/text in message".concat("\n------------------------"));
            return;
        }
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String messageText = message.getText();
        String userName = message.getFrom().getUserName();

        String logMessage = "From: ".concat(userName).concat("\nMessage: \"").concat(messageText).concat("\"");
        System.out.println(logMessage.concat("\n------------------------"));

        switch (currentState) {
            case IDLE_STATE:
                handleIdle(message);
//                System.out.println(currentState);
                break;
            case AWAITS_CATEGORY_STATE:
                handleAwaitCategory(message);
//                System.out.println(currentState);
                break;
            case AWAITS_EXPENSE_STATE:
                handleAwaitExpense(message);
//                System.out.println(currentState);
                break;
        }
    }

    //метод для смены состояния
    private void changeState(String newState, Long chatId, String messageText, List<String> buttonNames) {
        System.out.println(currentState + " -> " + newState);
        currentState = newState;
        sendKeyboard(chatId, messageText, buildKeyboard(buttonNames));
    }

    //метод обработки состояния IDLE
    private void handleIdle(Message incomingMessage) {
        String incomingText = incomingMessage.getText();
        Long chatId = incomingMessage.getChatId();
        switch (incomingText) {
            case "/start":
                sendKeyboard(chatId, String.format("Привет, %s!\nЯ помогу контролировать Ваши расходы!\nВнесите первую запись в формате: \"Категория Сумма\"", incomingMessage.getFrom().getUserName()), buildKeyboard(MAIN_BTN_LIST));
                break;
            case ADD_EXPENSE_BTN:
                changeState(AWAITS_CATEGORY_STATE, chatId, "Укажите категорию", null);
//                sendKeyboard(chatId, "Введите название категории и сумму трат в этой категории через пробел", buildKeyboard(MAIN_BTN_LIST));
                break;
            case SHOW_CATEGORIES_BTN:
                changeState(IDLE_STATE, chatId, getFormattedCategories(), MAIN_BTN_LIST);
//                sendKeyboard(chatId, getFormattedCategories(), buildKeyboard(MAIN_BTN_LIST));
                break;
            case SHOW_EXPENSES_BTN:
                changeState(IDLE_STATE, chatId, getFormattedExpenses(), MAIN_BTN_LIST);
//                sendKeyboard(chatId, getFormattedExpenses(), buildKeyboard(MAIN_BTN_LIST));
                break;
            default: {
                changeState(IDLE_STATE, chatId, "Такой команды нет", MAIN_BTN_LIST);
                break;
            }
        }
    }

    //метод обработки состояния AWAITS_CATEGORY_STATE
    private void handleAwaitCategory(Message incomingMessage) {
        String incomingText = incomingMessage.getText();
        Long chatId = incomingMessage.getChatId();
        EXPENSES.putIfAbsent(incomingText, new ArrayList<>()); //добавление только, если записей с таким ключом (категорией) еще нет,
        //категория соотносится с пустым списком расходов (для корректного добавления расхода в список на следующем шаге)
        lastCategory = incomingText; //запоминаем выбранную категорию
        changeState(AWAITS_EXPENSE_STATE, chatId, "Введите сумму расхода", null); //переход в состояние ввода ожидания расхода (без клавиатуры)
    }

    //метод обработки состояния AWAITS_EXPENSE_STATE
    private void handleAwaitExpense(Message incomingMessage) {
        Long chatId = incomingMessage.getChatId();
        if (lastCategory == null) {
            changeState(IDLE_STATE, chatId, "Не выбрана категория для добавления расхода\nПопробуйте начать с начала", MAIN_BTN_LIST);
            return;
        }
        String incomingText = incomingMessage.getText();
        Integer expense = Integer.parseInt(incomingText);
        EXPENSES.get(lastCategory).add(expense);
        changeState(IDLE_STATE, chatId, "Расход по категории \"" + lastCategory + "\" добавлен успешно", MAIN_BTN_LIST);
    }

    //метод получения списка добавленных категорий
    private String getFormattedCategories() {
        Set<String> categories = EXPENSES.keySet();
        if (categories.isEmpty())
            return "Нет добавленных категорий"; //если категорий нет - отправка соответствующего сообщения
        return String.join("\n", categories);
    }

    //метод получения расходов по добавленным категориям
    private String getFormattedExpenses() {
        Set<Map.Entry<String, List<Integer>>> expensesPerCategories = EXPENSES.entrySet();
        if (expensesPerCategories.isEmpty())
            return "Нет добавленных расходов"; //если расходов нет - отправка соответствующего сообщения
        StringBuilder formattedResult = new StringBuilder();
        for (Map.Entry<String, List<Integer>> category : expensesPerCategories) { //перебор всех категорий в списке трат
            StringBuilder categoryExpenses = new StringBuilder();
            for (Integer expense : category.getValue()) { //перебор всех трат по конкретной (текущей) категории
                categoryExpenses.append(expense).append(", "); //собираем все траты по категории в одну строку
            }
            //формирование результата в виде "Категория: 100, 200, 300, 400"
            formattedResult.append(category.getKey())
                    .append(": ")
                    .append(categoryExpenses.substring(0, categoryExpenses.length() - 2))
                    .append("\n");
        }
        return formattedResult.toString();
    }

    //отправка текстового сообщения и клавиатуры
    private void sendKeyboard(Long chatId, String messageText, ReplyKeyboard replyKeyboardMarkup) {
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

    //метод из занятия, в параметры принимает список из названий кнопок
    private ReplyKeyboard buildKeyboard(List<String> buttonNames) { //TODO: Добавить возможность управлять количеством кнопок в ряду
        if (buttonNames == null || buttonNames.isEmpty()) return new ReplyKeyboardRemove(true);
        List<KeyboardRow> rows = new ArrayList<>(); //список рядов кнопок
        for (String buttonName : buttonNames) { //перебор всех названий из списка
            KeyboardRow row = new KeyboardRow(); //создание отдельного ряда
            row.add(buttonName); //добавление кнопки в ряд
            rows.add(row); //добавление ряда в список рядов
        }

        return ReplyKeyboardMarkup.builder() //создание клавиатуры
                .keyboard(rows) //добавление кнопок (списка рядов кнопок) в клавиатуру
                .resizeKeyboard(true) //приведение размера кнопок к нормальному
                .build();
    }
}