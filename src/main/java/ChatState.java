import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChatState {
    private String state; //хранение состояние юзера
    private String data = null; //промежуточное поле для хранения последней выбранной юзером категории
    private Map<String, List<Integer>> expenses = new HashMap<>(); //хранение расходов конкретного юзера (чата) по категориям

    public ChatState(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Map<String, List<Integer>> getExpenses() {
        return expenses;
    }

    public void setExpenses(Map<String, List<Integer>> expenses) {
        this.expenses = expenses;
    }

    public String getFormattedCategories() { //метод получения списка категорий пользователя
        Set<String> categories = expenses.keySet();
        if (categories.isEmpty())
            return "Нет добавленных категорий"; //если категорий нет - отправка соответствующего сообщения
        return String.join("\n", categories);
    }

    public String getFormattedExpenses() { //метод получения списка расходов пользователя по категориям
        if (expenses.isEmpty())
            return "Нет добавленных расходов"; //если расходов нет - отправка соответствующего сообщения
        StringBuilder formattedResult = new StringBuilder();
        for (Map.Entry<String, List<Integer>> category : expenses.entrySet()) { //перебор всех категорий в списке трат
            String categoryName = category.getKey();
            List<Integer> categoryExpenses = category.getValue();
            formattedResult.append(categoryName)
                    .append(": ")
                    .append(getFormattedExpenses(categoryExpenses))
                    .append("\n");
        }
        return formattedResult.toString();
    }

    private String getFormattedExpenses(List<Integer> expensesPerCategory) {
        StringBuilder formattedResult = new StringBuilder();
        for (Integer expense : expensesPerCategory) { //перебор всех трат по конкретной (текущей) категории
            formattedResult.append(expense).append(", "); //собираем все траты по категории в одну строку
        } //формирование результата в виде "Категория: 100, 200, 300, 400"
        return formattedResult.substring(0, formattedResult.length() - 2);
    }
}