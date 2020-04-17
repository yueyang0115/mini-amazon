import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
# email address info
SMTP_SERVER = 'smtp.gmail.com:587'
USER_ACCOUNT = {
    'username': 'ece568noreply@gmail.com',
    'password': 'ece568code'
}


def send_email(receivers, text):
    msg_root = MIMEMultipart()
    msg_root['Subject'] = "Info from Mini Amazon 568"
    msg_root['To'] = ", ".join(receivers)
    msg_text = MIMEText(text)
    msg_root.attach(msg_text)

    smtp = smtplib.SMTP(SMTP_SERVER)
    smtp.starttls()
    smtp.login(USER_ACCOUNT["username"], USER_ACCOUNT["password"])
    smtp.sendmail(USER_ACCOUNT["username"], receivers, msg_root.as_string())
    smtp.quit()