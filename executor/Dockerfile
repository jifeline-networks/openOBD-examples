FROM python:3.12

RUN apt-get update && apt-get -y install awscli

# Install dependencies for the function-executor
RUN pip install executor
RUN pip install "fastapi[standard]"

# Install dependencies for the functions
RUN pip install openobd==1.18.2rc0

COPY . /opt/project/executor

WORKDIR /opt/project/executor

EXPOSE 8080
ENTRYPOINT [ "uvicorn", "server:api", "--host", "0.0.0.0", "--port", "8080" ]
