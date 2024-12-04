<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Confirmação de E-mail</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f4f4f4;
            color: #333;
            margin: 0;
            padding: 0;

        }
        .container {

            width: 100%;
            height: 100%;
            max-width: 600px;
            margin: 0 auto;
            padding: 20px;
            background-color: #fff;
            border-radius: 8px;
            box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
        }
        .header {
            text-align: center;
            padding: 10px;
        }
        .header h1 {
            color: #B993D6;
        }
        .content {
            text-align: center;
            padding: 20px;
        }
        .content p {
            font-size: 16px;
            line-height: 1.6;
        }
        .btn {
            display: inline-block;
            margin-top: 20px;
            padding: 12px 24px;
            color: #fff;
            background: linear-gradient(to right, #8CA6DB, #B993D6);
            text-decoration: none;
            font-weight: bold;
            border-radius: 5px;
        }
        .footer {
            margin-top: 20px;
            text-align: center;
            font-size: 12px;
            color: #777;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Confirmação de E-mail</h1>
        </div>
        <div class="content">
            <p>Olá,</p>
            <p>Obrigado por se registrar! Por favor, clique no botão abaixo para confirmar seu endereço de e-mail.</p>
            <a href="{{url}}" class="btn">Confirmar E-mail</a>
        </div>
        <div class="footer">
            <p>Se você não solicitou esta confirmação, ignore este e-mail.</p>
            <p>smartverse.com.br</p>
        </div>
    </div>
</body>
</html>