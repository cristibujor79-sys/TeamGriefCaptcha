TeamGriefCaptcha — исходный код Paper 1.21.11 / Java 21.

1. Установите JDK 21 и Apache Maven на ПК.
2. В папке проекта выполните: mvn clean package
3. Возьмите target/TeamGriefCaptcha-1.0.0.jar
4. Установите JAR ТОЛЬКО на отдельный сервер captcha (Paper), НЕ в папку plugins Velocity.
5. В plugins/TeamGriefCaptcha/config.yml укажите next-server: auth (или точное имя сервера регистрации из velocity.toml).
6. В velocity.toml: bungee-plugin-message-channel = true
7. В velocity.toml: [servers] укажите captcha и auth, а try = ["captcha"].
8. Перезапустите Velocity и Paper.

ВАЖНО: подключение к Paper-серверам должно быть закрыто от прямого доступа извне (firewall / локальная сеть). Иначе капчу можно обойти прямым входом на auth/hub/grief. На auth также нужна регистрация/авторизация. Этот пример — базовая капча, не полноценная антибот-система. При подключении после успешного ввода код проверяется сервером.
