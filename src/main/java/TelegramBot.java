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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    //константы - названия кнопок
    private static final String ADD_EXPENSE_BTN = "Добавить расход";
    private static final String SHOW_CATEGORIES_BTN = "Список категорий";
    private static final String SHOW_EXPENSES_BTN = "Список расходов";

    //список кнопок для основной клавиатуры
    private static final List<String> MAIN_BTN_LIST = List.of(ADD_EXPENSE_BTN, SHOW_CATEGORIES_BTN, SHOW_EXPENSES_BTN);

    //коллекция для хранения идентификатора чата (key) и объекта типа ChatState (value)
    private static final Map<Long, ChatState> CHATS = new HashMap<>();

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
            System.out.println("------------------------"
                    .concat("\nUnsupported update - no message/text in message")
                    .concat("\n------------------------"));
            return;
        }
        Message message = update.getMessage();
        Long chatId = message.getChatId(); //получение идентификатора конкретного чата
        CHATS.putIfAbsent(chatId, new ChatState(IDLE_STATE)); //если идентификатора чата в коллекции нет - добавление и создание пустого объекта класса ChatState
        ChatState currentChat = CHATS.get(chatId);

        String messageText = message.getText();
        String userName = message.getFrom().getUserName();
        String logMessage = "From: ".concat(userName).concat("\nMessage: \"").concat(messageText).concat("\"");
        System.out.println(logMessage.concat("\n------------------------"));

        switch (currentChat.getState()) { //использование состояния конкретного чата
            case IDLE_STATE:
                handleIdle(message, currentChat);
                break;
            case AWAITS_CATEGORY_STATE:
                handleAwaitCategory(message, currentChat);
                break;
            case AWAITS_EXPENSE_STATE:
                handleAwaitExpense(message, currentChat);
                break;
        }
    }

    //метод для смены состояния
    private void changeState(String newState,
                             Long chatId,
                             ChatState currentChat,
                             String messageText,
                             List<String> buttonNames) {
        System.out.println(currentChat.getState() + " -> " + newState + "\n------------------------".repeat(2));
        currentChat.setState(newState);
        sendKeyboard(chatId, messageText, buildKeyboard(buttonNames));
    }

    //метод обработки состояния IDLE
    private void handleIdle(Message incomingMessage, ChatState currentChat) {
        String incomingText = incomingMessage.getText();
        Long chatId = incomingMessage.getChatId();
        switch (incomingText) {
            case "/start":
                sendKeyboard(chatId, String.format("Привет, %s!\nЯ помогу контролировать Ваши расходы!\nДля добавления расхода нажмите кнопку \"Добавить расход\"", incomingMessage.getFrom().getUserName()), buildKeyboard(MAIN_BTN_LIST));
                break;
            case ADD_EXPENSE_BTN:
                changeState(AWAITS_CATEGORY_STATE, chatId, currentChat, "Укажите категорию", null);
                break;
            case SHOW_CATEGORIES_BTN:
                changeState(IDLE_STATE, chatId, currentChat, currentChat.getFormattedCategories(), MAIN_BTN_LIST);
                break;
            case SHOW_EXPENSES_BTN:
                changeState(IDLE_STATE, chatId, currentChat, currentChat.getFormattedExpenses(), MAIN_BTN_LIST);
                break;
            default: {
                changeState(IDLE_STATE, chatId, currentChat, "Такой команды нет", MAIN_BTN_LIST);
                break;
            }
        }
    }

    //метод обработки состояния AWAITS_CATEGORY_STATE
    private void handleAwaitCategory(Message incomingMessage, ChatState currentChat) {
        String incomingText = incomingMessage.getText();
        //форматирование введенной категории в формат "Категория" (1-я буква заглавная, остальные - строчные) с удалением пробелов слева и справа
        String formattedIncomingText = incomingText.substring(0, 1).toUpperCase()
                .concat(incomingText.substring(1).toLowerCase())
                .trim();
        Long chatId = incomingMessage.getChatId();
        currentChat.getExpenses().putIfAbsent(formattedIncomingText, new ArrayList<>()); //добавление только, если записей с таким ключом (категорией) еще нет,
        //категория соотносится с пустым списком расходов (для корректного добавления расхода в список на следующем шаге)
        currentChat.setData(formattedIncomingText); //запоминаем выбранную категорию для использования в методе ввода расхода
        changeState(AWAITS_EXPENSE_STATE, chatId, currentChat, "Введите сумму расхода", null); //переход в состояние ввода ожидания расхода (без клавиатуры)
    }

    //метод обработки состояния AWAITS_EXPENSE_STATE
    private void handleAwaitExpense(Message incomingMessage, ChatState currentChat) {
        Long chatId = incomingMessage.getChatId();
        if (currentChat.getData() == null) {
            changeState(IDLE_STATE, chatId, currentChat, "Не выбрана категория для добавления расхода\nПопробуйте начать с начала", MAIN_BTN_LIST);
            return;
        }
        String incomingText = incomingMessage.getText();
        Integer expense = Integer.parseInt(incomingText);
        currentChat.getExpenses().get(currentChat.getData()).add(expense);
        changeState(IDLE_STATE, chatId, currentChat, "Расход по категории \"" + currentChat.getData() + "\" добавлен успешно", MAIN_BTN_LIST);
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