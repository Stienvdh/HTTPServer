# HTTPServer

## Building the docker image
1. Go to /docker
2. `docker build .`
3. Run the image: `docker run -td -p 9999:9999 {image_id}`

## pushing the docker image
1. Tag the output of `docker build`, `docker tag {image_id} /{name}`
2. `docker push /{name}`