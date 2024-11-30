package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

//сервер, предназначенный для того, чтобы хостинг понимал, что приложение активно путем отправки запроса и ответа на него
public class DummyServer {
    public static void startServer() throws IOException {
        //1-ый параметр - порт, на котором будет запущен сервер, 2-ой - лог
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        HttpHandler getServerStatus = DummyServer::getServerStatus;
        //метод для приема запросов, 1-ый параметр - url, который будет слушаться, 2-ой - метод обработки запроса и отправки ответа
        server.createContext("/", getServerStatus);
    }

    private static void getServerStatus(HttpExchange exchange) throws IOException { //объект, который будет передаваться в вызов метода Java-Web-сервером
        String responseText = "Bot is running"; //текст ответа
        exchange.sendResponseHeaders(200, responseText.getBytes().length); //отправка заголовка ответа
        //1-ый параметр - код ответа (сервер реагирует на этот код), 2-ой - длина ответа
        exchange.getResponseBody().write(responseText.getBytes()); //запись текста ответа
        exchange.close(); //закрытие объекта
    }
}