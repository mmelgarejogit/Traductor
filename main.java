import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class JsonToXmlTranslator {

    private static final Set<String> SYNC_TOKENS = Set.of("{", "}", "[", "]", ",", ":");

    private static BufferedWriter writer;
    private static List<Token> tokens;
    private static int currentIndex;

    public static void main(String[] args) throws IOException {
        String input = new String(Files.readAllBytes(new File("fuente.txt").toPath()));
        tokens = new Lexer(input).tokenize();
        writer = new BufferedWriter(new FileWriter("output.xml"));

        try {
            parse();
        } catch (Exception e) {
            System.err.println("Parsing terminado debido a un error.");
        } finally {
            writer.close();
        }
    }

    private static void parse() throws IOException {
        Token tok = peek();
        if (tok.value.equals("{")) {
            match("{");
            writer.write("<personas>\n");
            parseArrayItems();
            match("}");
            writer.write("</personas>\n");
        } else {
            error("Se esperaba '{' al inicio del JSON.");
        }
    }

    private static void parseArrayItems() throws IOException {
        Token tok = peek();
        if (tok.value.equals("[")) {
            match("[");
            while (!peek().value.equals("]")) {
                parseObject();
                if (peek().value.equals(",")) {
                    match(",");
                } else {
                    break;
                }
            }
            match("]");
        } else {
            error("Se esperaba '[' para iniciar el array.");
        }
    }

    private static void parseObject() throws IOException {
        match("{");
        writer.write("\t<item>\n");
        while (!peek().value.equals("}")) {
            Token key = matchType(TokenType.STRING);
            match(":");
            Token value = peek();

            if (value.type == TokenType.STRING || value.type == TokenType.NUMBER ||
                value.type == TokenType.TRUE || value.type == TokenType.FALSE ||
                value.type == TokenType.NULL) {
                match();
                writeSimpleTag(key.value, value.value);
            } else if (value.value.equals("[")) {
                match("[");
                writer.write("\t\t<" + key.value + ">\n");
                while (!peek().value.equals("]")) {
                    parseObject();
                    if (peek().value.equals(",")) match(",");
                }
                match("]");
                writer.write("\t\t</" + key.value + ">\n");
            } else {
                error("Tipo insesperado.");
                panic();
            }

            if (peek().value.equals(",")) {
                match(",");
            } else {
                break;
            }
        }
        match("}");
        writer.write("\t</item>\n");
    }

    private static void writeSimpleTag(String key, String value) throws IOException {
        writer.write("\t\t<" + key + ">" + value + "</" + key + ">\n");
    }

    private static Token match() {
        return tokens.get(currentIndex++);
    }

    private static Token match(String expected) {
        Token token = peek();
        if (token.value.equals(expected)) {
            return match();
        } else {
            error("Token esperado: " + expected);
            panic();
            return new Token(TokenType.ERROR, expected);
        }
    }

    private static Token matchType(TokenType type) {
        Token token = peek();
        if (token.type == type) {
            return match();
        } else {
            error("Tipo de token inesperado: " + type);
            panic();
            return new Token(TokenType.ERROR, "?");
        }
    }

    private static Token peek() {
        return currentIndex < tokens.size() ? tokens.get(currentIndex) : new Token(TokenType.EOF, "EOF");
    }

    private static void error(String message) {
        System.err.println("[Error] " + message + " en token " + peek());
    }

    private static void panic() {
        while (currentIndex < tokens.size() && !SYNC_TOKENS.contains(peek().value)) {
            currentIndex++;
        }
        if (currentIndex < tokens.size()) currentIndex++;
    }
}

enum TokenType {
    L_BRACE, R_BRACE, L_BRACKET, R_BRACKET,
    COMMA, COLON,
    STRING, NUMBER,
    TRUE, FALSE, NULL,
    EOF, ERROR
}

class Token {
    TokenType type;
    String value;

    Token(TokenType type, String value) {
        this.type = type;
        this.value = value;
    }

    public String toString() {
        return "Token(" + type + ", '" + value + "')";
    }
}

class Lexer {
    private final String input;
    private final List<Token> tokens = new ArrayList<>();
    private int pos = 0;

    public Lexer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() {
        while (pos < input.length()) {
            char current = input.charAt(pos);
            if (Character.isWhitespace(current)) {
                pos++;
            } else if (current == '{') {
                tokens.add(new Token(TokenType.L_BRACE, "{")); pos++;
            } else if (current == '}') {
                tokens.add(new Token(TokenType.R_BRACE, "}")); pos++;
            } else if (current == '[') {
                tokens.add(new Token(TokenType.L_BRACKET, "[")); pos++;
            } else if (current == ']') {
                tokens.add(new Token(TokenType.R_BRACKET, "]")); pos++;
            } else if (current == ',') {
                tokens.add(new Token(TokenType.COMMA, ",")); pos++;
            } else if (current == ':') {
                tokens.add(new Token(TokenType.COLON, ":")); pos++;
            } else if (current == '"') {
                tokens.add(readString());
            } else if (Character.isDigit(current)) {
                tokens.add(readNumber());
            } else if (input.startsWith("true", pos)) {
                tokens.add(new Token(TokenType.TRUE, "true")); pos += 4;
            } else if (input.startsWith("false", pos)) {
                tokens.add(new Token(TokenType.FALSE, "false")); pos += 5;
            } else if (input.startsWith("null", pos)) {
                tokens.add(new Token(TokenType.NULL, "null")); pos += 4;
            } else {
                tokens.add(new Token(TokenType.ERROR, String.valueOf(current)));
                pos++;
            }
        }
        tokens.add(new Token(TokenType.EOF, "EOF"));
        return tokens;
    }

    private Token readString() {
        int start = ++pos;
        while (pos < input.length() && input.charAt(pos) != '"') {
            pos++;
        }
        String str = input.substring(start, pos);
        pos++; // skip closing quote
        return new Token(TokenType.STRING, str);
    }

    private Token readNumber() {
        int start = pos;
        while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
            pos++;
        }
        return new Token(TokenType.NUMBER, input.substring(start, pos));
    }
}
