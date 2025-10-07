# Social Media Backend - Render Deployment Guide

## Overview
This guide walks you through deploying your Spring Boot social media backend application to Render.com, including handling the Redis cache and database requirements.

## Prerequisites
- [Render.com](https://render.com) account
- GitHub repository with your code
- Cloudinary account for media storage
- Gmail account for email functionality

## Architecture Changes for Production

### Local vs Production Setup
| Component | Local Setup | Production Setup |
|-----------|-------------|------------------|
| Database | MySQL Master-Slave (3 instances) | Single PostgreSQL |
| Cache | Local Redis | Render Redis Service |
| File Storage | Local uploads | Cloudinary CDN |
| Configuration | application.properties | application-prod.properties |

## Step-by-Step Deployment

### 1. Prepare Your Repository

1. **Commit all the new files** to your GitHub repository:
   ```bash
   git add .
   git commit -m "Add Render deployment configuration"
   git push origin main
   ```

2. **Files added for deployment:**
   - `Dockerfile` - Container configuration
   - `application-prod.properties` - Production configuration
   - `render.yaml` - Render service configuration
   - `ProductionDatabaseConfig.java` - Simplified database config

### 2. Create Render Services

#### A. Create PostgreSQL Database
1. Go to [Render Dashboard](https://dashboard.render.com)
2. Click **"New +"** → **"PostgreSQL"**
3. Configure:
   - **Name**: `social-media-db`
   - **Database**: `socialmedia`
   - **User**: `socialmedia_user`
   - **Region**: Choose closest to your users
   - **Plan**: Start with **Free** (can upgrade later)
4. Click **"Create Database"**
5. **Save the connection details** (you'll need them)

#### B. Create Redis Instance
1. Click **"New +"** → **"Redis"**
2. Configure:
   - **Name**: `social-media-redis`
   - **Region**: Same as your database
   - **Plan**: Start with **Free**
3. Click **"Create Redis"**

#### C. Deploy the Application
1. Click **"New +"** → **"Web Service"**
2. Connect your GitHub repository
3. Configure:
   - **Name**: `social-media-backend`
   - **Branch**: `main`
   - **Runtime**: `Docker`
   - **Region**: Same as your database/Redis
   - **Plan**: **Starter** ($7/month) or **Standard** for better performance

### 3. Configure Environment Variables

In your Render web service, add these environment variables:

#### Required Variables:
```bash
# Spring Profile
SPRING_PROFILES_ACTIVE=prod

# Database (Auto-populated if using render.yaml)
DATABASE_URL=postgresql://user:password@host:port/database
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password

# Redis (Auto-populated if using render.yaml)
REDIS_URL=redis://host:port

# JWT Security (IMPORTANT: Generate a strong secret)
JWT_SECRET=your-super-secure-jwt-secret-key-here

# Cloudinary Configuration
CLOUDINARY_CLOUD_NAME=your_cloudinary_cloud_name
CLOUDINARY_API_KEY=your_cloudinary_api_key
CLOUDINARY_API_SECRET=your_cloudinary_api_secret

# Email Configuration
MAIL_USERNAME=your_gmail@gmail.com
MAIL_PASSWORD=your_gmail_app_password

# Application URL (Update with your actual Render URL)
APP_BASE_URL=https://your-app-name.onrender.com
```

### 4. Cloudinary Setup (Media Storage)

1. **Sign up** at [Cloudinary](https://cloudinary.com)
2. Get your credentials from the dashboard:
   - Cloud Name
   - API Key
   - API Secret
3. **Add these to your Render environment variables**

### 5. Gmail App Password Setup

1. **Enable 2FA** on your Gmail account
2. **Generate App Password**:
   - Go to Google Account settings
   - Security → 2-Step Verification → App passwords
   - Generate password for "Mail"
3. **Use this app password** (not your regular password) in `MAIL_PASSWORD`

### 6. Deploy and Test

1. **Deploy**: Click "Deploy Latest Commit" in Render
2. **Monitor logs** during deployment
3. **Test endpoints**:
   - Health check: `https://your-app.onrender.com/actuator/health`
   - API: `https://your-app.onrender.com/api/auth/register`

## Troubleshooting

### Common Issues:

#### 1. Database Connection Failed
```bash
# Check environment variables
DATABASE_URL format: postgresql://username:password@host:port/database
```

#### 2. Redis Connection Failed
```bash
# Verify Redis URL format
REDIS_URL=redis://host:port
# Or with password: redis://:password@host:port
```

#### 3. Application Won't Start
```bash
# Check logs for:
- Java version compatibility (using Java 21)
- Missing environment variables
- Database migration issues
```

#### 4. JWT Token Issues
```bash
# Ensure JWT_SECRET is set and secure
JWT_SECRET=at-least-32-characters-long-secret-key
```

### Performance Optimization

#### Memory Settings
The Dockerfile includes optimized JVM settings:
```bash
JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"
```

#### Database Connection Pool
Production config includes optimized pool settings:
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

## Monitoring and Maintenance

### Health Checks
- **Endpoint**: `/actuator/health`
- **Checks**: Database, Redis, Application status

### Scaling Options
1. **Vertical Scaling**: Upgrade Render plan
2. **Database Scaling**: Upgrade PostgreSQL plan
3. **Redis Scaling**: Upgrade Redis plan

### Backup Strategy
1. **Database**: Render provides automated backups
2. **Media Files**: Stored in Cloudinary (redundant)
3. **Configuration**: Keep environment variables documented

## Cost Estimation

### Free Tier (Development):
- PostgreSQL: Free (limited storage)
- Redis: Free (limited memory)
- Web Service: $0 (sleeps after inactivity)

### Production Tier:
- PostgreSQL: $7+/month
- Redis: $7+/month  
- Web Service: $7+/month
- **Total**: ~$21+/month

## Security Checklist

- [ ] Strong JWT secret generated
- [ ] Database IP restrictions configured
- [ ] Environment variables secured
- [ ] HTTPS enforced (automatic on Render)
- [ ] Gmail app password used (not regular password)
- [ ] Cloudinary API keys secured

## Next Steps

1. **Custom Domain**: Configure your own domain in Render
2. **SSL Certificate**: Automatic with custom domains
3. **Monitoring**: Set up alerts for downtime
4. **CI/CD**: Configure automatic deployments on git push
5. **Load Testing**: Test with realistic traffic

## Support

- **Render Documentation**: https://render.com/docs
- **Spring Boot on Render**: https://render.com/docs/spring-boot
- **PostgreSQL on Render**: https://render.com/docs/databases