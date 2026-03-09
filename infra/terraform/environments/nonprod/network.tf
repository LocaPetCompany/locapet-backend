resource "aws_vpc" "nonprod" {
  cidr_block           = "10.20.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name        = "locapet-nonprod-vpc"
    Project     = "locapet"
    Environment = "nonprod"
    ManagedBy   = "terraform"
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_subnet" "public_1" {
  vpc_id                  = aws_vpc.nonprod.id
  cidr_block              = "10.20.1.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = {
    Name        = "locapet-nonprod-public-1"
    Project     = "locapet"
    Environment = "nonprod"
    Tier        = "public"
    ManagedBy   = "terraform"
  }
}

resource "aws_subnet" "public_2" {
  vpc_id                  = aws_vpc.nonprod.id
  cidr_block              = "10.20.2.0/24"
  availability_zone       = data.aws_availability_zones.available.names[1]
  map_public_ip_on_launch = true

  tags = {
    Name        = "locapet-nonprod-public-2"
    Project     = "locapet"
    Environment = "nonprod"
    Tier        = "public"
    ManagedBy   = "terraform"
  }
}

resource "aws_subnet" "private_db_1" {
  vpc_id            = aws_vpc.nonprod.id
  cidr_block        = "10.20.11.0/24"
  availability_zone = data.aws_availability_zones.available.names[0]

  tags = {
    Name        = "locapet-nonprod-private-db-1"
    Project     = "locapet"
    Environment = "nonprod"
    Tier        = "private-db"
    ManagedBy   = "terraform"
  }
}

resource "aws_subnet" "private_db_2" {
  vpc_id            = aws_vpc.nonprod.id
  cidr_block        = "10.20.12.0/24"
  availability_zone = data.aws_availability_zones.available.names[1]

  tags = {
    Name        = "locapet-nonprod-private-db-2"
    Project     = "locapet"
    Environment = "nonprod"
    Tier        = "private-db"
    ManagedBy   = "terraform"
  }
}

resource "aws_internet_gateway" "nonprod" {
  vpc_id = aws_vpc.nonprod.id

  tags = {
    Name        = "locapet-nonprod-igw"
    Project     = "locapet"
    Environment = "nonprod"
    ManagedBy   = "terraform"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.nonprod.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.nonprod.id
  }

  tags = {
    Name        = "locapet-nonprod-public-rt"
    Project     = "locapet"
    Environment = "nonprod"
    Tier        = "public"
    ManagedBy   = "terraform"
  }
}

resource "aws_route_table_association" "public_1" {
  subnet_id      = aws_subnet.public_1.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "public_2" {
  subnet_id      = aws_subnet.public_2.id
  route_table_id = aws_route_table.public.id
}