FROM python:3
ENV PYTHONUNBUFFERED 1
RUN chmod 777 /code/amazon/static/img
RUN mkdir /code
WORKDIR /code
ADD requirements.txt /code/
RUN pip install -r requirements.txt
ADD . /code/