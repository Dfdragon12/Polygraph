# Build en dos etapas: la primera compila el bundle estático con Node, la segunda solo sirve esos
# archivos con nginx — la imagen final no carga con Node ni node_modules, solo los archivos ya
# compilados (dist/) y nginx.
FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
