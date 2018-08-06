Text2Geolocation - A REST microservice returning latitude and longitude from text
---------------------------------------------------------------------------------

It uses DBpedia as a backend to do the heavy lifting.

#### Build and run
```bash
sbt docker
# or, to skip tests:
# sbt 'set test in assembly := {}' docker

docker-compose build
docker-compose up -d
``` 

#### Usage:
```bash
curl -H "Content-Type: application/json" \
    --request POST \
    -d '{"name":"Naples","country":"it"}' \
    http://localhost:8101/coordinates
  
curl -H "Content-Type: application/json" \
    --request POST \
    -d '{"name":"Naples","country":"us"}' \
    http://localhost:8101/coordinates  
  
curl -H "Content-Type: application/json" \
    --request POST \
    -d '{"name":"Rome"}' \
    http://localhost:8101/coordinates  
```

A Postman Api example is also included in the ```postman``` directory.


