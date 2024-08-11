#!/bin/bash

check_image_update() {
    local image=$1
    local tag=${2:-latest}  # Default to "latest" if no tag is provided

    # Get the current image digest
    current_digest=$(sudo docker inspect --format='{{index .RepoDigests 0}}' "${image}:${tag}" | cut -d'@' -f2)

    # Get the latest digest from Docker Hub
    latest_digest=$(curl -s "https://hub.docker.com/v2/repositories/${image}/tags/${tag}" | jq -r '.digest')

    if [ "$current_digest" != "$latest_digest" ]; then
        echo "Update available for ${image}:${tag}"
        echo "Pulling update now"
        sudo docker-compose -f /home/alike/docker-compose.yml pull
    else
        echo "No update available for ${image}:${tag}"
    fi
}
while IFS= read -r line; do
    if [[ $line =~ image:\ (.+):(.+) ]]; then
        image="${BASH_REMATCH[1]}"
        tag="${BASH_REMATCH[2]}"
    elif [[ $line =~ image:\ (.+) ]]; then
        image="${BASH_REMATCH[1]}"
        tag="latest"
    fi
    if [ -n "$image" ]; then
        check_image_update "$image" "$tag"
    fi
done < <(grep 'image:' /home/alike/docker-compose.yml)

