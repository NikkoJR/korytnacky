import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Korytnacky extends Application {
    // Размер окна по условию задания: 800x800
    public static final int SIZE = 800;

    // Размер картинки черепашки на экране
    private static final double ICON_SIZE = 45;

    // Список всех черепашек.
    // synchronizedList нужен, потому что с этим списком работают разные потоки.
    private final List<Turtle> turtles = Collections.synchronizedList(new ArrayList<>());

    // Один Canvas нужен для линий.
    // Линии должны оставаться на экране и не стираться.
    private Canvas trailCanvas;

    // Второй Canvas нужен для иконок черепашек.
    // Его можно очищать и перерисовывать при каждом движении.
    private Canvas turtleCanvas;

    // GraphicsContext — объект, через который мы реально рисуем на Canvas.
    private GraphicsContext trailGc;
    private GraphicsContext turtleGc;

    // Картинка черепашки из файла turtle.png
    private Image turtleImage;

    @Override
    public void start(Stage stage) throws Exception {
        // Создаём Canvas для линий и Canvas для черепашек.
        trailCanvas = new Canvas(SIZE, SIZE);
        turtleCanvas = new Canvas(SIZE, SIZE);

        // Получаем объекты для рисования.
        trailGc = trailCanvas.getGraphicsContext2D();
        turtleGc = turtleCanvas.getGraphicsContext2D();

        // Pane содержит оба Canvas.
        // Сначала trailCanvas, потом turtleCanvas.
        // Это значит, что черепашки будут рисоваться поверх линий.
        Pane root = new Pane(trailCanvas, turtleCanvas);

        // Создаём сцену размером 800x800.
        Scene scene = new Scene(root, SIZE, SIZE);

        // Настройка окна.
        stage.setTitle("Korytnacky");
        stage.setScene(scene);
        stage.show();

        // Загружаем картинку черепашки.
        turtleImage = loadTurtleImage();

        // Если аргумент командной строки не передали,
        // читаем файл turtles.txt.
        String fileName;
        if (getParameters().getRaw().isEmpty()) {
            fileName = "turtles.txt";
        } else {
            // Если аргумент передали, используем его как имя файла.
            // Например: turtles1.txt или turtles2.txt.
            fileName = getParameters().getRaw().get(0);
        }

        // Читаем программы черепашек из файла.
        // Каждая строка файла становится отдельной программой.
        List<List<Instruction>> programs = loadPrograms(Paths.get(fileName));

        // Создаём черепашку для каждой строки файла.
        for (int i = 0; i < programs.size(); i++) {
            // id = i + 1, потому что нумерация черепашек должна быть с 1.
            Turtle turtle = new Turtle(i + 1, programs.get(i));
            turtles.add(turtle);
        }

        // Обработка клика мышкой.
        // При клике ищем ближайшую живую черепашку и ставим её на паузу
        // или снимаем с паузы.
        turtleCanvas.setOnMouseClicked(event -> {
            toggleNearestTurtle(event.getX(), event.getY());
        });

        // В начале рисуем всех черепашек в центре.
        redrawTurtles();

        // Запускаем каждую черепашку в отдельном потоке.
        // Это нужно, потому что у каждой черепашки свой sleepTime
        // и свой независимый жизненный цикл.
        for (Turtle turtle : turtles) {
            Thread thread = new Thread(turtle, "Turtle-" + turtle.id);

            // Daemon-поток автоматически завершится при закрытии приложения.
            thread.setDaemon(true);

            thread.start();
        }
    }

    private Image loadTurtleImage() throws IOException {
        // Сначала пробуем найти turtle.png рядом с проектом.
        Path path = Paths.get("turtle.png");

        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                return new Image(input);
            }
        }

        // Если не нашли рядом с проектом,
        // пробуем найти файл в resources.
        InputStream resource = getClass().getResourceAsStream("/turtle.png");

        if (resource != null) {
            try (InputStream input = resource) {
                return new Image(input);
            }
        }

        // Если файл не найден вообще, выбрасываем ошибку.
        throw new IOException("Subor turtle.png sa nenasiel.");
    }

    private List<List<Instruction>> loadPrograms(Path path) throws IOException {
        // Читаем все строки файла.
        List<String> lines = Files.readAllLines(path);

        // Тут будет результат:
        // список программ, где каждая программа — список инструкций.
        List<List<Instruction>> result = new ArrayList<>();

        // Каждая строка файла — программа одной черепашки.
        for (String line : lines) {
            line = line.trim();

            // Пустые строки пропускаем.
            if (line.isEmpty()) {
                continue;
            }

            // Инструкции в строке разделены символом ;
            String[] instructionTexts = line.split(";");

            // Программа одной конкретной черепашки.
            List<Instruction> program = new ArrayList<>();

            for (String instructionText : instructionTexts) {
                instructionText = instructionText.trim();

                if (instructionText.isEmpty()) {
                    continue;
                }

                // Каждая инструкция имеет вид:
                // NAME ARGUMENT
                // Например:
                // TURN +91
                // FORWARD 2
                String[] parts = instructionText.split("\\s+");

                if (parts.length != 2) {
                    throw new IllegalArgumentException("Zla instrukcia: " + instructionText);
                }

                // Название команды переводим в uppercase,
                // чтобы не зависеть от регистра.
                String name = parts[0].toUpperCase();

                // Аргумент оставляем как String.
                // Это важно, потому что нужно отличать:
                // "5" от "+5" и "-5".
                String argument = parts[1];

                program.add(new Instruction(name, argument));
            }

            // Если в строке были инструкции, добавляем программу в результат.
            if (!program.isEmpty()) {
                result.add(program);
            }
        }

        return result;
    }

    private void toggleNearestTurtle(double mouseX, double mouseY) {
        Turtle nearest = null;
        double bestDistanceSquared = Double.MAX_VALUE;

        // Список synchronized, поэтому при обходе лучше явно синхронизироваться.
        synchronized (turtles) {
            for (Turtle turtle : turtles) {
                // Берём безопасную копию состояния черепашки.
                TurtleState state = turtle.snapshot();

                // Мёртвую черепашку нельзя ставить на паузу.
                if (!state.alive) {
                    continue;
                }

                // mouseX и mouseY — экранные координаты клика.
                // Позиция черепашки хранится в логических координатах,
                // поэтому переводим её через toScreenX и toScreenY.
                double dx = toScreenX(state.x) - mouseX;
                double dy = toScreenY(state.y) - mouseY;

                // Для сравнения расстояний можно не брать корень.
                // Квадрат расстояния тоже подходит.
                double distanceSquared = dx * dx + dy * dy;

                if (distanceSquared < bestDistanceSquared) {
                    bestDistanceSquared = distanceSquared;
                    nearest = turtle;
                }
            }
        }

        // Если нашли ближайшую живую черепашку,
        // переключаем её состояние paused.
        if (nearest != null) {
            nearest.togglePaused();
        }
    }

    private void redrawTurtles() {
        // Очищаем только Canvas с иконками.
        // Canvas с линиями НЕ очищаем, иначе пропадёт весь нарисованный след.
        turtleGc.clearRect(0, 0, SIZE, SIZE);

        synchronized (turtles) {
            for (Turtle turtle : turtles) {
                TurtleState state = turtle.snapshot();

                // Рисуем только живых черепашек.
                if (!state.alive) {
                    continue;
                }

                drawTurtleIcon(state.x, state.y, state.angle);
            }
        }
    }

    private void drawTurtleIcon(double x, double y, int angle) {
        // Переводим логические координаты в экранные.
        double screenX = toScreenX(x);
        double screenY = toScreenY(y);

        // save/restore нужны, чтобы поворот и перенос координат
        // не влияли на следующее рисование.
        turtleGc.save();

        // Переносим начало координат в точку, где стоит черепашка.
        turtleGc.translate(screenX, screenY);

        // Поворачиваем картинку.
        // angle — угол движения.
        // +90 нужен, потому что картинка черепашки смотрит вверх,
        // а угол 0 по формуле означает движение вправо.
        turtleGc.rotate(angle + 90);

        // Рисуем картинку так, чтобы её центр был в позиции черепашки.
        turtleGc.drawImage(
                turtleImage,
                -ICON_SIZE / 2,
                -ICON_SIZE / 2,
                ICON_SIZE,
                ICON_SIZE
        );

        turtleGc.restore();
    }

    private boolean allTurtlesDead() {
        synchronized (turtles) {
            for (Turtle turtle : turtles) {
                if (turtle.isAlive()) {
                    return false;
                }
            }
        }

        return true;
    }

    private static double toScreenX(double x) {
        // Логическая координата x = 0 должна быть в центре окна.
        return SIZE / 2.0 + x;
    }

    private static double toScreenY(double y) {
        // Логическая координата y = 0 должна быть в центре окна.
        return SIZE / 2.0 + y;
    }

    private static boolean isOutside(double x, double y) {
        // Проверяем, находится ли черепашка вне экрана.
        double screenX = toScreenX(x);
        double screenY = toScreenY(y);

        return screenX < 0 || screenX > SIZE || screenY < 0 || screenY > SIZE;
    }

    private static Color toColor(int colorInt) {
        // Перевод int-цвета в JavaFX Color.
        // Например:
        // 16711680 = красный
        // 255 = синий
        return Color.rgb(
                colorInt >> 16 & 0xff,
                colorInt >> 8 & 0xff,
                colorInt & 0xff
        );
    }

    private static int applyArgument(int oldValue, String argument) {
        // Если аргумент начинается с + или -,
        // значит нужно изменить старое значение.
        //
        // Например:
        // oldValue = 10, argument = "+5" -> 15
        // oldValue = 10, argument = "-3" -> 7
        if (argument.startsWith("+") || argument.startsWith("-")) {
            return oldValue + Integer.parseInt(argument);
        }

        // Если аргумент без знака,
        // значит нужно установить новое значение.
        //
        // Например:
        // oldValue = 10, argument = "100" -> 100
        return Integer.parseInt(argument);
    }

    private static void sleepQuietly(int milliseconds) {
        // Если задержка 0 или отрицательная, просто ничего не делаем.
        if (milliseconds <= 0) {
            return;
        }

        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            // Если поток прервали, восстанавливаем interrupted-флаг.
            Thread.currentThread().interrupt();
        }
    }

    private static class Instruction {
        // Название инструкции:
        // SLEEP, COLOR, STEP, TURN, FORWARD
        final String name;

        // Аргумент инструкции:
        // например "5", "+5", "-5"
        final String argument;

        Instruction(String name, String argument) {
            this.name = name;
            this.argument = argument;
        }
    }

    private static class TurtleState {
        // Это маленький класс-копия состояния черепашки.
        // Он нужен, чтобы безопасно читать состояние из другого потока.

        final double x;
        final double y;
        final int angle;
        final boolean alive;

        TurtleState(double x, double y, int angle, boolean alive) {
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.alive = alive;
        }
    }

    private class Turtle implements Runnable {
        // Номер черепашки.
        // По заданию после смерти надо вывести номер 1..N.
        final int id;

        // Программа этой черепашки.
        // Каждая черепашка имеет свой список инструкций.
        final List<Instruction> program;

        // Логическая позиция черепашки.
        // В начале [0, 0].
        private double x = 0;
        private double y = 0;

        // Угол движения в градусах.
        // В начале 0.
        private int angle = 0;

        // Размер шага.
        // В начале 0.
        private int stepSize = 0;

        // Цвет линии.
        // В начале 0, то есть black.
        private int colorInt = 0;

        // Задержка между движениями.
        // В начале 0.
        private int sleepTime = 0;

        // Пройденная дистанция в пикселях.
        private double distance = 0;

        // alive = true, пока черепашка не вышла за экран.
        private volatile boolean alive = true;

        // paused = true, если черепашка стоит на паузе после клика.
        private volatile boolean paused = false;

        Turtle(int id, List<Instruction> program) {
            this.id = id;
            this.program = program;
        }

        @Override
        public void run() {
            // Индекс текущей инструкции.
            int instructionIndex = 0;

            // Главный цикл жизни черепашки.
            // Он работает, пока черепашка живая.
            while (alive) {
                // Если черепашка на паузе,
                // она ничего не выполняет, просто ждёт.
                if (paused) {
                    sleepQuietly(20);
                    continue;
                }

                // Берём текущую инструкцию.
                Instruction instruction = program.get(instructionIndex);

                // Переходим к следующей инструкции.
                // % program.size() делает программу циклической.
                //
                // Например, если инструкций 5:
                // 0, 1, 2, 3, 4, 0, 1, 2, ...
                instructionIndex = (instructionIndex + 1) % program.size();

                // Выполняем инструкцию.
                execute(instruction);
            }

            // Когда черепашка умерла, выводим её номер и путь.
            System.out.printf("Turtle %d: %.2f px%n", id, distance);

            // Обновляем GUI после смерти черепашки.
            Platform.runLater(() -> {
                redrawTurtles();

                // Если умерли все черепашки, закрываем приложение.
                if (allTurtlesDead()) {
                    Platform.exit();
                }
            });
        }

        private void execute(Instruction instruction) {
            switch (instruction.name) {
                case "SLEEP":
                    // SLEEP меняет задержку.
                    // Если аргумент без знака — устанавливает.
                    // Если с + или - — изменяет старое значение.
                    sleepTime = applyArgument(sleepTime, instruction.argument);
                    break;

                case "COLOR":
                    // COLOR меняет цвет линии.
                    colorInt = applyArgument(colorInt, instruction.argument);
                    break;

                case "STEP":
                    // STEP меняет размер шага.
                    stepSize = applyArgument(stepSize, instruction.argument);
                    break;

                case "TURN":
                    // TURN меняет угол.
                    angle = applyArgument(angle, instruction.argument);
                    break;

                case "FORWARD":
                    // FORWARD — единственная команда, которая двигает черепашку.
                    // Аргумент FORWARD просто число, которое умножается на stepSize.
                    forward(Integer.parseInt(instruction.argument));
                    break;

                default:
                    throw new IllegalArgumentException("Neznama instrukcia: " + instruction.name);
            }
        }

        private void forward(int argument) {
            double oldX;
            double oldY;
            double newX;
            double newY;

            int usedAngle;
            int usedColor;
            int usedSleep;

            // synchronized нужен, чтобы изменение позиции и чтение позиции
            // из другого потока не конфликтовали.
            synchronized (this) {
                // Запоминаем старую позицию.
                oldX = x;
                oldY = y;

                // Запоминаем угол, с которым будем двигаться.
                usedAngle = angle;

                // Длина движения:
                // FORWARD argument * текущий stepSize.
                //
                // Например:
                // stepSize = 5
                // FORWARD 2
                // movementLength = 10
                double movementLength = argument * stepSize;

                // Формула из задания.
                // Math.toRadians переводит градусы в радианы,
                // потому что Math.cos и Math.sin работают с радианами.
                newX = x + movementLength * Math.cos(Math.toRadians(usedAngle));
                newY = y + movementLength * Math.sin(Math.toRadians(usedAngle));

                // Обновляем позицию черепашки.
                x = newX;
                y = newY;

                // Считаем длину пройденного отрезка.
                double dx = newX - oldX;
                double dy = newY - oldY;

                distance += Math.sqrt(dx * dx + dy * dy);

                // Запоминаем цвет и sleep,
                // которые были актуальны во время движения.
                usedColor = colorInt;
                usedSleep = sleepTime;

                // Если черепашка вышла за экран,
                // помечаем её как мёртвую.
                if (isOutside(x, y)) {
                    alive = false;
                }
            }

            // Рисовать в JavaFX можно только из JavaFX Application Thread.
            // Поэтому используем Platform.runLater.
            Platform.runLater(() -> {
                // Ставим цвет линии.
                trailGc.setStroke(toColor(usedColor));

                // Рисуем линию от старой позиции к новой.
                trailGc.strokeLine(
                        toScreenX(oldX),
                        toScreenY(oldY),
                        toScreenX(newX),
                        toScreenY(newY)
                );

                // Перерисовываем иконки черепашек.
                redrawTurtles();
            });

            // После движения ждём sleepTime миллисекунд.
            // Это создаёт анимацию.
            sleepQuietly(usedSleep);
        }

        synchronized TurtleState snapshot() {
            // Возвращаем копию состояния.
            // Так внешний код не читает поля напрямую.
            return new TurtleState(x, y, angle, alive);
        }

        void togglePaused() {
            // Мёртвую черепашку уже нельзя ставить на паузу.
            if (alive) {
                paused = !paused;
            }
        }

        boolean isAlive() {
            return alive;
        }
    }

    public static void main(String[] args) {
        // Запуск JavaFX-приложения.
        launch(args);
    }
}
