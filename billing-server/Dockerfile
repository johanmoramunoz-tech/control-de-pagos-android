FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY package*.json ./
RUN npm install --omit=dev --ignore-scripts && npm cache clean --force
COPY verify-purchase.cjs server.cjs ./
USER node
EXPOSE 8080
CMD ["node","server.cjs"]
